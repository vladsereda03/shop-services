package shop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import shop.auth.model.Role;
import shop.auth.model.User;
import shop.auth.repository.UserRepository;
import shop.event.UserRegisteredEvent;

// full-context integration test of the authorization server: real PostgreSQL and Kafka
// in containers. Covers the producer side of the user-registered flow (cart tests the
// consumer side) and smoke-tests token issuing
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

  private static final String USER_REGISTERED_TOPIC = "user-registered-events-topic";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container @ServiceConnection
  static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");

  // production reads the signing keys from env vars (no defaults);
  // the test generates its own RSA pair to stay hermetic
  private static final KeyPair RSA_KEY_PAIR = generateRsaKeyPair();

  @DynamicPropertySource
  static void jwtKeyProperties(DynamicPropertyRegistry registry) {
    registry.add("jwk.key-id", () -> "test-key");
    registry.add(
        "jwt.public-key",
        () -> Base64.getEncoder().encodeToString(RSA_KEY_PAIR.getPublic().getEncoded()));
    registry.add(
        "jwt.private-key",
        () -> Base64.getEncoder().encodeToString(RSA_KEY_PAIR.getPrivate().getEncoded()));
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @BeforeEach
  void cleanUp() {
    userRepository.deleteAll();
  }

  // --- registration → DB + Kafka event ---

  @Test
  void signupPersistsUserAndPublishesEventToKafka() throws Exception {
    mockMvc
        .perform(
            post("/account/signup")
                .with(csrf())
                .param("username", "integration_user")
                .param("password", "password123")
                .param("email", "integration@example.com")
                .param("fullName", "Integration Test")
                .param("phone", "+380501234567"))
        .andExpect(status().is3xxRedirection());

    User saved = userRepository.findByUsername("integration_user").orElseThrow();
    assertThat(saved.getPassword()).startsWith("{bcrypt}");
    assertThat(saved.getRoles()).containsExactly(Role.USER);

    // the event must actually reach the broker, not just leave the KafkaTemplate
    try (Consumer<String, UserRegisteredEvent> consumer = testConsumer()) {
      consumer.subscribe(List.of(USER_REGISTERED_TOPIC));
      ConsumerRecord<String, UserRegisteredEvent> record =
          KafkaTestUtils.getSingleRecord(consumer, USER_REGISTERED_TOPIC, Duration.ofSeconds(30));
      assertThat(record.key()).isEqualTo(String.valueOf(saved.getId()));
      assertThat(record.value().getUserId()).isEqualTo(saved.getId());
      assertThat(record.value().getUsername()).isEqualTo("integration_user");
    }
  }

  @Test
  void invalidSignupIsRejectedWithoutSideEffects() throws Exception {
    mockMvc
        .perform(
            post("/account/signup")
                .with(csrf())
                .param("username", "short") // username must be at least 8 chars
                .param("password", "password123")
                .param("email", "integration@example.com")
                .param("fullName", "Integration Test")
                .param("phone", "+380501234567"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account/signup/form"));

    assertThat(userRepository.findAll()).isEmpty();
  }

  // --- token issuing (smoke) ---

  @Test
  void clientCredentialsGrantIssuesSignedJwtWithClientScopes() throws Exception {
    // scope must be requested explicitly: SAS leaves authorized scopes (and the claim)
    // empty otherwise. The real services send it from their client registration
    MvcResult result =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .param("grant_type", "client_credentials")
                    .param("scope", "products.read products.write")
                    .with(httpBasic("cart-service", "cart-service-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").exists())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andReturn();

    String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");
    String header = decodeJwtPart(accessToken, 0);
    String payload = decodeJwtPart(accessToken, 1);

    assertThat(header).contains("\"kid\":\"test-key\"");
    assertThat((String) JsonPath.read(payload, "$.iss")).isEqualTo("http://auth.local:9000");
    assertThat((List<String>) JsonPath.read(payload, "$.scope"))
        .containsExactlyInAnyOrder("products.read", "products.write");
  }

  @Test
  void wrongClientSecretIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/oauth2/token")
                .param("grant_type", "client_credentials")
                .with(httpBasic("cart-service", "wrong-secret")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void jwksEndpointServesTheSigningKeyAnonymously() throws Exception {
    // resource servers fetch this without any token — it must stay open
    mockMvc
        .perform(get("/oauth2/jwks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].kid").value("test-key"));
  }

  // --- helpers ---

  private static KeyPair generateRsaKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String decodeJwtPart(String jwt, int index) {
    return new String(
        Base64.getUrlDecoder().decode(jwt.split("\\.")[index]), StandardCharsets.UTF_8);
  }

  private Consumer<String, UserRegisteredEvent> testConsumer() {
    Map<String, Object> props =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "auth-integration-test",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            new JsonDeserializer<>(UserRegisteredEvent.class, false))
        .createConsumer();
  }
}
