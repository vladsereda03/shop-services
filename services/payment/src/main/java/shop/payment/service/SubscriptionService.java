package shop.payment.service;

import com.liqpay.LiqPay;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import shop.payment.client.CartClient;
import shop.payment.client.OrderClient;
import shop.payment.config.LiqPayProperties;
import shop.payment.model.Subscription;
import shop.payment.model.SupportedCurrency;
import shop.payment.model.dto.CartDTO;
import shop.payment.model.dto.CartItemDTO;
import shop.payment.model.dto.OrderItemDTO;
import shop.payment.model.dto.SubscriptionForm;
import shop.payment.repository.SubscriptionRepository;

@Service
public class SubscriptionService {

  private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

  private static final DateTimeFormatter LIQPAY_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final SubscriptionRepository subscriptionRepository;
  private final LiqPayProperties liqPayProperties;
  private final CartClient cartClient;
  private final OrderClient orderClient;
  private final boolean registerInLiqPay;

  public SubscriptionService(
      SubscriptionRepository subscriptionRepository,
      LiqPayProperties liqPayProperties,
      CartClient cartClient,
      OrderClient orderClient,
      @Value("${payment.subscription.register-in-liqpay}") boolean registerInLiqPay) {
    this.subscriptionRepository = subscriptionRepository;
    this.liqPayProperties = liqPayProperties;
    this.cartClient = cartClient;
    this.orderClient = orderClient;
    this.registerInLiqPay = registerInLiqPay;
  }

  // form-shape validation (required fields, periodicity pattern) happens at the MVC
  // edge via Bean Validation on SubscriptionForm — here only business rules remain
  @Transactional
  public Subscription subscribe(Long userId, SubscriptionForm form) {
    CartDTO cart = cartClient.getCart(userId);

    if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot create a subscription from an empty cart");
    }

    Map<Long, Subscription.SubscriptionItem> items =
        cart.getItems().stream()
            .collect(
                Collectors.toMap(
                    CartItemDTO::getGoodId,
                    item ->
                        new Subscription.SubscriptionItem(
                            item.getQuantity(), item.getPriceKopeck())));

    // save first: LiqPay needs order_id = subscription.id, and any later failure rolls it back
    Subscription subscription =
        subscriptionRepository.saveAndFlush(
            Subscription.builder()
                .userId(userId)
                .phone(form.getPhone())
                .currencyCode(SupportedCurrency.UAH.getCode())
                .periodicity(form.getPeriodicity())
                .startDate(form.getStartDate().atTime(form.getStartTime()))
                .items(new HashMap<>(items))
                .build());

    if (registerInLiqPay) {
      registerInLiqPay(subscription, form);
    } else {
      // LiqPay sandbox does not support subscriptions — the local scheduler emulates the charges
      logger.info(
          "LiqPay subscribe skipped for subscription {}: emulation mode", subscription.getId());
    }

    // clearing goes last: a failure rolls the subscription back
    // (checkout-clear keeps the stock reserved — the goods are sold, not returned)
    cartClient.clearAfterCheckout(userId);

    logger.info(
        "Subscription {} created for user {} ({}, {} items)",
        subscription.getId(),
        userId,
        form.getPeriodicity(),
        items.size());
    return subscription;
  }

  public List<Subscription> getMySubscriptions(Long userId) {
    return subscriptionRepository.findAllByUserIdOrderByIdDesc(userId);
  }

  // a confirmed recurring charge turns the stored snapshot into an order;
  // called by the LiqPay callback and by the local schedule emulator
  public void createOrderFromSubscription(Subscription subscription) {
    List<OrderItemDTO> items =
        subscription.getItems().entrySet().stream()
            .map(
                entry ->
                    new OrderItemDTO(
                        entry.getKey(),
                        entry.getValue().getQuantity(),
                        entry.getValue().getPriceKopeck()))
            .toList();

    orderClient.createOrder(subscription.getUserId(), items);

    logger.info(
        "Recurring order created from subscription {} for user {}",
        subscription.getId(),
        subscription.getUserId());
  }

  // registers the recurring charge: LiqPay will debit the card on schedule
  // and send a callback to /subscription/payment on every charge
  private void registerInLiqPay(Subscription subscription, SubscriptionForm form) {
    Map<String, String> params = new HashMap<>();
    params.put("version", "7");
    params.put("sandbox", "1");

    params.put("action", "subscribe");
    params.put("subscribe", "1");

    params.put("phone", form.getPhone());
    params.put("amount", String.valueOf(subscription.calculatePrice()));
    params.put("currency", subscription.getCurrencyCode());
    params.put(
        "description", "Оформлення платіжної підписки, user_id: " + subscription.getUserId());
    params.put("order_id", String.valueOf(subscription.getId()));

    params.put("subscribe_date_start", LIQPAY_DATE_FORMAT.format(subscription.getStartDate()));
    params.put("subscribe_periodicity", subscription.getPeriodicity());
    params.put("card", form.getCardNumber());
    params.put("card_exp_month", String.valueOf(form.getExpMonth()));
    params.put("card_exp_year", String.valueOf(form.getExpYear()));
    params.put("card_cvv", form.getCvv());

    try {
      LiqPay liqpay = new LiqPay(liqPayProperties.publicKey(), liqPayProperties.privateKey());
      Map<String, Object> response = liqpay.api("request", params);
      logger.info(
          "LiqPay subscribe response for subscription {}: {}", subscription.getId(), response);

      Object status = response.get("status");
      if ("error".equals(status) || "failure".equals(status)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "LiqPay rejected the subscription: " + response.get("err_description"));
      }
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "LiqPay subscribe request failed", e);
    }
  }
}
