package shop.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import shop.cart.client.ProductClient;
import shop.cart.config.KafkaErrorConfig;
import shop.cart.model.Cart;
import shop.cart.model.dto.ProductDTO;
import shop.cart.repository.CartRepository;
import shop.event.UserRegisteredEvent;

// full-context integration test: real PostgreSQL and Kafka in containers,
// product-service is the only stubbed piece (its OAuth2 RestClient is replaced below)
@SpringBootTest(
    properties = {
      "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
      // single-broker Kafka in the container: the DLT topic cannot have 3 replicas
      "app.kafka.topic.dlt.replicas=1"
    })
@AutoConfigureMockMvc
@Testcontainers
class CartIT {

  private static final String PRODUCT_BASE_URL = "http://product.local:8082";
  private static final String USER_REGISTERED_TOPIC = "user-registered-events-topic";
  private static final long USER_ID = 42L;

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container @ServiceConnection
  // 3.8.x: the 3.9.0 image validates advertised.listeners during storage format,
  // which breaks the Testcontainers startup scheme (listeners are injected post-start)
  static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");

  @Autowired private MockMvc mockMvc;

  @Autowired private CartRepository cartRepository;

  @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;

  @Autowired private MockRestServiceServer productServer;

  @Autowired private ProductClient productClient;

  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @BeforeEach
  void cleanUp() {
    productServer.reset();
    cartRepository.deleteAll();
  }

  // --- Kafka consumer ---

  @Test
  void userRegisteredEventCreatesCart() {
    kafkaTemplate.send(USER_REGISTERED_TOPIC, new UserRegisteredEvent(101L, "newuser"));

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(cartRepository.findByUserId(101L)).isPresent());
  }

  @Test
  void duplicateUserRegisteredEventDoesNotCreateSecondCart() {
    cartRepository.saveAndFlush(Cart.builder().userId(202L).build());

    kafkaTemplate.send(USER_REGISTERED_TOPIC, new UserRegisteredEvent(202L, "existing"));
    // the topic has a single partition, so once the marker event is processed
    // the duplicate before it is guaranteed to have been handled too
    kafkaTemplate.send(USER_REGISTERED_TOPIC, new UserRegisteredEvent(203L, "marker"));

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(cartRepository.findByUserId(203L)).isPresent());
    assertThat(cartRepository.findAll()).extracting(Cart::getUserId).containsOnlyOnce(202L);
  }

  @Test
  void poisonMessageIsDeadLetteredWithoutBlockingThePartition() throws Exception {
    String bootstrap = kafka.getBootstrapServers();

    try (KafkaConsumer<String, String> deadLetters = stringConsumer(bootstrap)) {
      deadLetters.subscribe(List.of(KafkaErrorConfig.USER_REGISTERED_DLT));
      deadLetters.poll(Duration.ofMillis(500)); // force partition assignment before producing

      // a value the JsonDeserializer cannot turn into a UserRegisteredEvent
      try (KafkaProducer<String, String> raw = stringProducer(bootstrap)) {
        raw.send(new ProducerRecord<>(USER_REGISTERED_TOPIC, "poison", "not-a-valid-event")).get();
      }

      // a valid event queued after the poison on the same single partition: were the partition
      // wedged by the poison, this cart would never be created
      kafkaTemplate.send(USER_REGISTERED_TOPIC, new UserRegisteredEvent(909L, "after-poison"));
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(cartRepository.findByUserId(909L)).isPresent());

      // and the poison itself was routed to the dead-letter topic instead of looping forever
      await()
          .atMost(Duration.ofSeconds(15))
          .untilAsserted(
              () -> assertThat(deadLetters.poll(Duration.ofMillis(500)).count()).isPositive());
    }
  }

  private static KafkaConsumer<String, String> stringConsumer(String bootstrap) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "cart-dlt-it");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new KafkaConsumer<>(props);
  }

  private static KafkaProducer<String, String> stringProducer(String bootstrap) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new KafkaProducer<>(props);
  }

  // --- REST + stock reservation ---

  @Test
  void addItemPersistsCartAndReservesStock() throws Exception {
    productServer
        .expect(requestTo(PRODUCT_BASE_URL + "/api/products/5"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":5,\"name\":\"Конструктор\",\"priceKopeck\":1500,\"quantity\":10}",
                MediaType.APPLICATION_JSON));
    productServer
        .expect(requestTo(PRODUCT_BASE_URL + "/api/products/5/reserve?quantity=2"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());

    mockMvc
        .perform(
            post("/carts/my/items")
                .param("goodId", "5")
                .param("quantity", "2")
                .with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(42))
        .andExpect(jsonPath("$.items[0].goodId").value(5))
        .andExpect(jsonPath("$.items[0].quantity").value(2))
        .andExpect(jsonPath("$.totalPrice").value(30.0));

    productServer.verify();
    Cart saved = cartRepository.findByUserId(USER_ID).orElseThrow();
    assertThat(saved.getItems().get(5L).getQuantity()).isEqualTo(2);
    assertThat(saved.getItems().get(5L).getPriceKopeck()).isEqualTo(1500L);
  }

  @Test
  void failedReservationRollsBackTheWholeCartTransaction() throws Exception {
    productServer
        .expect(requestTo(PRODUCT_BASE_URL + "/api/products/5"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":5,\"name\":\"Конструктор\",\"priceKopeck\":1500,\"quantity\":1}",
                MediaType.APPLICATION_JSON));
    productServer
        .expect(requestTo(PRODUCT_BASE_URL + "/api/products/5/reserve?quantity=2"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.CONFLICT));

    mockMvc
        .perform(
            post("/carts/my/items")
                .param("goodId", "5")
                .param("quantity", "2")
                .with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
        .andExpect(status().isConflict());

    // reservation goes last inside the transaction: its 409 must undo the flushed cart insert
    assertThat(cartRepository.findByUserId(USER_ID)).isEmpty();
  }

  @Test
  void clearCartReleasesEveryReservedItem() throws Exception {
    Cart cart = Cart.builder().userId(USER_ID).build();
    cart.addItem(5L, 2, 1500L);
    cart.addItem(6L, 1, 500L);
    cartRepository.saveAndFlush(cart);

    productServer
        .expect(requestTo(PRODUCT_BASE_URL + "/api/products/5/release?quantity=2"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());
    productServer
        .expect(requestTo(PRODUCT_BASE_URL + "/api/products/6/release?quantity=1"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());

    mockMvc
        .perform(delete("/carts/my").with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());

    productServer.verify();
    assertThat(cartRepository.findByUserId(USER_ID).orElseThrow().getItems()).isEmpty();
  }

  // --- resilience on the product leg ---

  @Test
  void retryRecoversGetProductFromTransientFailures() {
    circuitBreakerRegistry.circuitBreaker("product").reset();
    productServer
        .expect(ExpectedCount.times(2), requestTo(PRODUCT_BASE_URL + "/api/products/5"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withServerError());
    productServer
        .expect(requestTo(PRODUCT_BASE_URL + "/api/products/5"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":5,\"name\":\"Конструктор\",\"priceKopeck\":1500,\"quantity\":10}",
                MediaType.APPLICATION_JSON));

    // two 5xx answers are retried away; the third attempt succeeds transparently
    ProductDTO product = productClient.getProduct(5L);

    assertThat(product.getPriceKopeck()).isEqualTo(1500L);
    productServer.verify();
  }

  @Test
  void openCircuitBreakerFailsFastWith503() {
    CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("product");
    breaker.reset();
    try {
      productServer
          .expect(ExpectedCount.manyTimes(), requestTo(PRODUCT_BASE_URL + "/api/products/5"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(withServerError());

      // every retry attempt is recorded by the breaker, so a few failing calls
      // cross minimum-number-of-calls=5 at a 100% failure rate and open it
      for (int i = 0; i < 3; i++) {
        try {
          productClient.getProduct(5L);
        } catch (Exception expectedUntilOpen) {
          // 5xx while closed, then the 503 fallback once the breaker opens
        }
      }
      assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

      // fail-fast: rejected by the breaker before any HTTP, mapped to 503 by the fallback
      assertThatThrownBy(() -> productClient.getProduct(5L))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(
              e ->
                  assertThat(((ResponseStatusException) e).getStatusCode())
                      .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    } finally {
      // the context is shared with the other tests: leave the breaker closed
      breaker.reset();
    }
  }

  // --- security rules ---

  @Test
  void internalApiRequiresServiceScopes() throws Exception {
    mockMvc.perform(get("/carts/my")).andExpect(status().isUnauthorized());

    // a user token without carts.read must not reach the internal API
    mockMvc
        .perform(
            get("/internal/carts/" + USER_ID).with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/internal/carts/" + USER_ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_carts.read"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(42));
  }

  // replaces the OAuth2-interceptor RestClient from RestClientConfig: tests must not
  // fetch client-credentials tokens, and product-service answers come from the mock server
  @TestConfiguration
  static class ProductClientStubConfig {

    @Bean
    RestClient.Builder productStubRestClientBuilder() {
      return RestClient.builder();
    }

    @Bean
    MockRestServiceServer productServer(RestClient.Builder productStubRestClientBuilder) {
      // unordered: clearCart iterates a HashMap, so release calls come in any order
      return MockRestServiceServer.bindTo(productStubRestClientBuilder)
          .ignoreExpectOrder(true)
          .build();
    }

    @Bean
    @Primary
    RestClient productStubRestClient(
        RestClient.Builder productStubRestClientBuilder, MockRestServiceServer productServer) {
      return productStubRestClientBuilder.build();
    }
  }
}
