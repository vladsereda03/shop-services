package shop.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import shop.payment.client.CartClient;
import shop.payment.client.OrderClient;
import shop.payment.config.LiqPayProperties;
import shop.payment.model.Subscription;
import shop.payment.model.dto.SubscriptionForm;
import shop.payment.repository.SubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

  private static final String CART_BASE_URL = "http://cart.test";
  private static final String ORDER_BASE_URL = "http://order.test";
  private static final long USER_ID = 42L;

  @Mock private SubscriptionRepository subscriptionRepository;

  private MockRestServiceServer server;
  private SubscriptionService subscriptionService;

  @BeforeEach
  void setUp() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    server = MockRestServiceServer.bindTo(restClientBuilder).build();
    // bare clients: without Spring AOP their resilience annotations are inert,
    // so the unit tests exercise pure subscription logic
    CartClient cartClient = new CartClient(restClientBuilder.build());
    ReflectionTestUtils.setField(cartClient, "cartBaseUrl", CART_BASE_URL);
    OrderClient orderClient = new OrderClient(restClientBuilder.build());
    ReflectionTestUtils.setField(orderClient, "orderBaseUrl", ORDER_BASE_URL);
    // registerInLiqPay=false — the sandbox has no subscriptions, charges are emulated by the
    // scheduler
    subscriptionService =
        new SubscriptionService(
            subscriptionRepository,
            new LiqPayProperties("test_public_key", "test_private_key"),
            cartClient,
            orderClient,
            false);
  }

  // --- subscribe ---

  // form-shape validation (periodicity pattern, required dates) lives on
  // SubscriptionForm as Bean Validation now and is exercised at the MVC edge
  // by PaymentIntegrationTest.invalidSubscriptionFormIsRejectedWithProblemJson

  @Test
  void emptyCartIsRejectedWith400AndNothingIsSaved() {
    server
        .expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":1,\"userId\":42,\"items\":[],\"totalPrice\":0.0}",
                MediaType.APPLICATION_JSON));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> subscriptionService.subscribe(USER_ID, validForm()))
        .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

    verifyNoInteractions(subscriptionRepository);
    server.verify();
  }

  @Test
  void subscribeSnapshotsCartSavesSubscriptionAndClearsCart() {
    // expectations are ordered: cart snapshot first, checkout-clear last
    server
        .expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":1,\"userId\":42,\"items\":["
                    + "{\"goodId\":5,\"quantity\":2,\"priceKopeck\":1500}],\"totalPrice\":30.0}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID + "/checkout-clear"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());
    when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Subscription result = subscriptionService.subscribe(USER_ID, validForm());

    server.verify();
    ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
    verify(subscriptionRepository).saveAndFlush(captor.capture());
    Subscription saved = captor.getValue();
    assertThat(result).isSameAs(saved);
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getPhone()).isEqualTo("380501234567");
    assertThat(saved.getCurrencyCode()).isEqualTo("UAH");
    assertThat(saved.getPeriodicity()).isEqualTo("month");
    assertThat(saved.getStartDate()).isEqualTo(LocalDateTime.of(2026, 7, 10, 15, 0));
    assertThat(saved.getItems()).hasSize(1);
    assertThat(saved.getItems().get(5L).getQuantity()).isEqualTo(2);
    assertThat(saved.getItems().get(5L).getPriceKopeck()).isEqualTo(1500L);
  }

  @Test
  void checkoutClearFailurePropagatesAfterSaveSoTransactionRollsBack() {
    server
        .expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":1,\"userId\":42,\"items\":["
                    + "{\"goodId\":5,\"quantity\":2,\"priceKopeck\":1500}],\"totalPrice\":30.0}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID + "/checkout-clear"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());
    when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatExceptionOfType(HttpServerErrorException.class)
        .isThrownBy(() -> subscriptionService.subscribe(USER_ID, validForm()));

    // save happened before the failing clear — @Transactional rollback undoes it
    verify(subscriptionRepository).saveAndFlush(any(Subscription.class));
    server.verify();
  }

  // --- createOrderFromSubscription ---

  @Test
  void recurringChargeTurnsSnapshotIntoOrder() {
    server
        .expect(requestTo(ORDER_BASE_URL + "/internal/orders/" + USER_ID))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json("[{\"goodId\":5,\"quantity\":2,\"priceKopeck\":1500}]", true))
        .andRespond(withSuccess());
    Subscription subscription =
        Subscription.builder()
            .id(77L)
            .userId(USER_ID)
            .items(Map.of(5L, new Subscription.SubscriptionItem(2, 1500L)))
            .build();

    subscriptionService.createOrderFromSubscription(subscription);

    server.verify();
  }

  private static SubscriptionForm validForm() {
    return new SubscriptionForm(
        "380501234567",
        LocalTime.of(15, 0),
        LocalDate.of(2026, 7, 10),
        "month",
        "4242424242424242",
        12,
        2030,
        "123");
  }
}
