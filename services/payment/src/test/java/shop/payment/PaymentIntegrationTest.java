package shop.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.json.JSONObject;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import shop.payment.model.Subscription;
import shop.payment.repository.SubscriptionRepository;

// full-context integration test: real PostgreSQL in a container, cart/order calls stubbed.
// LiqPay keys are pinned so the test stays hermetic even when real LIQPAY_* env vars are set
@SpringBootTest(
    properties = {
      "liqpay.public-key=test_public_key",
      "liqpay.private-key=test_private_key",
      "payment.subscription.register-in-liqpay=false"
    })
@AutoConfigureMockMvc
@Testcontainers
class PaymentIntegrationTest {

  private static final String PRIVATE_KEY = "test_private_key";
  private static final String CART_BASE_URL = "http://cart.local:8083";
  private static final String ORDER_BASE_URL = "http://localhost:8084";
  private static final long USER_ID = 42L;

  private static final String CART_WITH_TWO_ITEMS =
      """
            {"id":1,"userId":42,"items":[
              {"goodId":5,"quantity":2,"priceKopeck":1500},
              {"goodId":6,"quantity":1,"priceKopeck":500}
            ],"totalPrice":35.0}""";

  private static final String SUBSCRIPTION_FORM =
      """
            {"phone":"380501234567","startTime":"15:00","startDate":"2026-08-01",
             "periodicity":"month","cardNumber":"4242424242424242",
             "expMonth":12,"expYear":2030,"cvv":"123"}""";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private SubscriptionRepository subscriptionRepository;

  @Autowired private MockRestServiceServer shopServer;

  @BeforeEach
  void cleanUp() {
    shopServer.reset();
    subscriptionRepository.deleteAll();
  }

  // --- subscriptions ---

  @Test
  void subscribeSnapshotsCartIntoDatabaseAndClearsCart() throws Exception {
    shopServer
        .expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(CART_WITH_TWO_ITEMS, MediaType.APPLICATION_JSON));
    shopServer
        .expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID + "/checkout-clear"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());

    mockMvc
        .perform(
            post("/subscriptions/my")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SUBSCRIPTION_FORM)
                .with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.periodicity").value("month"))
        .andExpect(jsonPath("$.currencyCode").value("UAH"))
        .andExpect(jsonPath("$.startDate").value("2026-08-01T15:00:00"))
        .andExpect(jsonPath("$.totalPrice").value(35.0));

    shopServer.verify();
    List<Subscription> subscriptions = subscriptionRepository.findAllByUserIdOrderByIdDesc(USER_ID);
    assertThat(subscriptions).hasSize(1);
    // the @ElementCollection snapshot survived a round-trip through the real schema
    Subscription saved = subscriptions.getFirst();
    assertThat(saved.getItems().get(5L).getQuantity()).isEqualTo(2);
    assertThat(saved.getItems().get(6L).getPriceKopeck()).isEqualTo(500L);
  }

  @Test
  void failedCartClearRollsBackTheSubscription() {
    shopServer
        .expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(CART_WITH_TWO_ITEMS, MediaType.APPLICATION_JSON));
    shopServer
        .expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID + "/checkout-clear"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    // payment has no advice for upstream 5xx: the exception escapes MockMvc,
    // in the real servlet container it turns into a 500 via the ERROR dispatch
    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    post("/subscriptions/my")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBSCRIPTION_FORM)
                        .with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID)))))
        .hasRootCauseInstanceOf(HttpServerErrorException.InternalServerError.class);

    // clearing goes last inside the transaction: its failure must undo the flushed subscription
    assertThat(subscriptionRepository.findAll()).isEmpty();
  }

  @Test
  void mySubscriptionsRequiresJwt() throws Exception {
    mockMvc.perform(get("/subscriptions/my")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(get("/subscriptions/my").with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // --- schedule emulator query ---

  @Test
  void scheduleQueryFindsOnlyDueSubscriptionsOfMatchingPeriodicity() {
    Subscription dueMonthly =
        subscriptionRepository.saveAndFlush(
            subscriptionOf("month", LocalDateTime.now().minusDays(1)));
    subscriptionRepository.saveAndFlush(subscriptionOf("month", LocalDateTime.now().plusDays(1)));
    subscriptionRepository.saveAndFlush(subscriptionOf("week", LocalDateTime.now().minusDays(1)));

    List<Subscription> due =
        subscriptionRepository.findByPeriodicityAndStartDateBefore("month", LocalDateTime.now());

    assertThat(due).extracting(Subscription::getId).containsExactly(dueMonthly.getId());
  }

  // --- LiqPay callbacks (anonymous by design) ---

  @Test
  void callbacksAreOpenToAnonymousCallsButSignatureIsEnforced() throws Exception {
    String data = encode(new JSONObject().put("status", "success").put("info", "42"));

    // 403 (not 401): permitAll lets LiqPay in without a token, the signature rejects the forgery
    mockMvc
        .perform(post("/payment/new").param("data", data).param("signature", "forged-signature"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/subscription/payment")
                .param("data", data)
                .param("signature", "forged-signature"))
        .andExpect(status().isForbidden());
  }

  @Test
  void failedPaymentCallbackIsAcceptedWithoutCreatingAnOrder() throws Exception {
    String data =
        encode(
            new JSONObject().put("status", "failure").put("order_id", "uuid-1").put("info", "42"));

    mockMvc
        .perform(post("/payment/new").param("data", data).param("signature", sign(data)))
        .andExpect(status().isOk());

    shopServer.verify(); // no checkout call reached order-service
  }

  @Test
  void successfulPaymentCallbackTriggersCheckoutInOrderService() throws Exception {
    shopServer
        .expect(requestTo(ORDER_BASE_URL + "/internal/orders/" + USER_ID + "/checkout"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());
    String data =
        encode(
            new JSONObject().put("status", "success").put("order_id", "uuid-1").put("info", "42"));

    mockMvc
        .perform(post("/payment/new").param("data", data).param("signature", sign(data)))
        .andExpect(status().isOk());

    shopServer.verify();
  }

  // --- helpers ---

  private static Subscription subscriptionOf(String periodicity, LocalDateTime startDate) {
    return Subscription.builder()
        .userId(USER_ID)
        .phone("380501234567")
        .currencyCode("UAH")
        .periodicity(periodicity)
        .startDate(startDate)
        .build();
  }

  private static String encode(JSONObject payload) {
    return Base64.getEncoder().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
  }

  // same formula as production: base64(sha1(private_key + data + private_key))
  private static String sign(String data) {
    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      return Base64.getEncoder()
          .encodeToString(
              sha1.digest((PRIVATE_KEY + data + PRIVATE_KEY).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  // replaces the OAuth2-interceptor RestClient from RestClientConfig: tests must not
  // fetch client-credentials tokens, and cart/order answers come from the mock server
  @TestConfiguration
  static class ShopClientStubConfig {

    @Bean
    RestClient.Builder shopStubRestClientBuilder() {
      return RestClient.builder();
    }

    @Bean
    MockRestServiceServer shopServer(RestClient.Builder shopStubRestClientBuilder) {
      return MockRestServiceServer.bindTo(shopStubRestClientBuilder).build();
    }

    @Bean
    @Primary
    RestClient shopStubRestClient(
        RestClient.Builder shopStubRestClientBuilder, MockRestServiceServer shopServer) {
      return shopStubRestClientBuilder.build();
    }
  }
}
