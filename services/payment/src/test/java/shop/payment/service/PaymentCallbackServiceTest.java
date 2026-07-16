package shop.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import shop.payment.client.OrderClient;
import shop.payment.config.LiqPayProperties;
import shop.payment.model.ProcessedCallback;
import shop.payment.model.Subscription;
import shop.payment.repository.ProcessedCallbackRepository;
import shop.payment.repository.SubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackServiceTest {

  private static final String PRIVATE_KEY = "test_private_key";
  private static final String ORDER_BASE_URL = "http://order.test";

  @Mock private SubscriptionRepository subscriptionRepository;

  @Mock private SubscriptionService subscriptionService;

  @Mock private ProcessedCallbackRepository processedCallbackRepository;

  private MockRestServiceServer orderServer;
  private PaymentCallbackService callbackService;

  @BeforeEach
  void setUp() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    orderServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    // a bare OrderClient: without Spring AOP its resilience annotations are inert,
    // so the unit tests exercise pure callback logic
    OrderClient orderClient = new OrderClient(restClientBuilder.build());
    ReflectionTestUtils.setField(orderClient, "orderBaseUrl", ORDER_BASE_URL);
    callbackService =
        new PaymentCallbackService(
            new LiqPayProperties("test_public_key", PRIVATE_KEY),
            orderClient,
            subscriptionRepository,
            subscriptionService,
            processedCallbackRepository);
  }

  // --- one-time payment callback ---

  @Test
  void paymentCallbackWithWrongSignatureIsRejectedWith403() {
    String data = encode(paymentPayload("success", 42L));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> callbackService.processPaymentCallback(data, "forged-signature"))
        .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

    orderServer.verify();
  }

  @Test
  void paymentCallbackWithNonPaidStatusIsIgnored() {
    String data = encode(paymentPayload("failure", 42L));

    assertThatCode(() -> callbackService.processPaymentCallback(data, sign(data)))
        .doesNotThrowAnyException();

    orderServer.verify();
  }

  @Test
  void successStatusTriggersCheckoutForUserFromInfo() {
    orderServer
        .expect(requestTo(ORDER_BASE_URL + "/internal/orders/42/checkout"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());
    String data = encode(paymentPayload("success", 42L));

    callbackService.processPaymentCallback(data, sign(data));

    orderServer.verify();
  }

  @Test
  void sandboxStatusAlsoCountsAsPaid() {
    orderServer
        .expect(requestTo(ORDER_BASE_URL + "/internal/orders/42/checkout"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());
    String data = encode(paymentPayload("sandbox", 42L));

    callbackService.processPaymentCallback(data, sign(data));

    orderServer.verify();
  }

  @Test
  void duplicateCallbackWithEmptyCartIsSwallowed() {
    orderServer
        .expect(requestTo(ORDER_BASE_URL + "/internal/orders/42/checkout"))
        .andRespond(withBadRequest());
    String data = encode(paymentPayload("success", 42L));

    assertThatCode(() -> callbackService.processPaymentCallback(data, sign(data)))
        .doesNotThrowAnyException();
  }

  @Test
  void orderServiceFailurePropagatesSoLiqPayRetries() {
    orderServer
        .expect(requestTo(ORDER_BASE_URL + "/internal/orders/42/checkout"))
        .andRespond(withServerError());
    String data = encode(paymentPayload("success", 42L));

    assertThatExceptionOfType(HttpServerErrorException.class)
        .isThrownBy(() -> callbackService.processPaymentCallback(data, sign(data)));
  }

  // --- idempotency by payment_id ---

  @Test
  void duplicatePaymentIdIsAbsorbedWithoutSecondCheckout() {
    when(processedCallbackRepository.existsById(555L)).thenReturn(true);
    String data = encode(paymentPayload("success", 42L).put("payment_id", 555L));

    assertThatCode(() -> callbackService.processPaymentCallback(data, sign(data)))
        .doesNotThrowAnyException();

    orderServer.verify(); // no checkout expectation was registered — none may happen
  }

  @Test
  void firstPaymentIdIsRecordedBeforeCheckout() {
    orderServer
        .expect(requestTo(ORDER_BASE_URL + "/internal/orders/42/checkout"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());
    when(processedCallbackRepository.existsById(555L)).thenReturn(false);
    String data = encode(paymentPayload("success", 42L).put("payment_id", 555L));

    callbackService.processPaymentCallback(data, sign(data));

    ArgumentCaptor<ProcessedCallback> saved = ArgumentCaptor.forClass(ProcessedCallback.class);
    verify(processedCallbackRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getPaymentId()).isEqualTo(555L);
    orderServer.verify();
  }

  @Test
  void duplicateSubscriptionPaymentIdDoesNotCreateSecondOrder() {
    Subscription subscription = Subscription.builder().id(77L).userId(42L).build();
    when(subscriptionRepository.findById(77L)).thenReturn(Optional.of(subscription));
    when(processedCallbackRepository.existsById(888L)).thenReturn(true);
    String data = encode(subscriptionPayload("success", "77").put("payment_id", 888L));

    callbackService.processSubscriptionCallback(data, sign(data));

    verifyNoInteractions(subscriptionService);
  }

  // --- recurring subscription callback ---

  @Test
  void subscriptionCallbackWithWrongSignatureIsRejectedWith403() {
    String data = encode(subscriptionPayload("success", "77"));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> callbackService.processSubscriptionCallback(data, "forged-signature"))
        .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

    verifyNoInteractions(subscriptionRepository, subscriptionService);
  }

  @Test
  void subscriptionRegisteredStatusIsIgnored() {
    String data = encode(subscriptionPayload("subscribed", "77"));

    callbackService.processSubscriptionCallback(data, sign(data));

    verifyNoInteractions(subscriptionRepository, subscriptionService);
  }

  @Test
  void subscriptionCallbackWithNonNumericOrderIdIsIgnored() {
    String data = encode(subscriptionPayload("success", "not-a-subscription-id"));

    callbackService.processSubscriptionCallback(data, sign(data));

    verifyNoInteractions(subscriptionRepository, subscriptionService);
  }

  @Test
  void subscriptionCallbackForUnknownSubscriptionIsIgnored() {
    when(subscriptionRepository.findById(77L)).thenReturn(Optional.empty());
    String data = encode(subscriptionPayload("success", "77"));

    callbackService.processSubscriptionCallback(data, sign(data));

    verifyNoInteractions(subscriptionService);
  }

  @Test
  void paidSubscriptionCallbackCreatesOrderFromSnapshot() {
    Subscription subscription = Subscription.builder().id(77L).userId(42L).build();
    when(subscriptionRepository.findById(77L)).thenReturn(Optional.of(subscription));
    String data = encode(subscriptionPayload("success", "77"));

    callbackService.processSubscriptionCallback(data, sign(data));

    verify(subscriptionService).createOrderFromSubscription(subscription);
  }

  // --- helpers ---

  private static JSONObject paymentPayload(String status, long userId) {
    return new JSONObject()
        .put("status", status)
        .put("order_id", "one-time-order-uuid")
        .put("info", String.valueOf(userId));
  }

  private static JSONObject subscriptionPayload(String status, String orderId) {
    return new JSONObject().put("status", status).put("order_id", orderId);
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
}
