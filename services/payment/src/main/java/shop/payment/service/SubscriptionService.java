package shop.payment.service;

import com.liqpay.LiqPay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import shop.payment.config.LiqPayProperties;
import shop.payment.model.SupportedCurrency;
import shop.payment.model.Subscription;
import shop.payment.model.dto.CartDTO;
import shop.payment.model.dto.CartItemDTO;
import shop.payment.model.dto.OrderItemDTO;
import shop.payment.model.dto.SubscriptionForm;
import shop.payment.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    // must match both the form options and the task.cron.* config keys
    private static final Set<String> PERIODICITIES = Set.of("day", "week", "month", "year");

    private static final DateTimeFormatter LIQPAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SubscriptionRepository subscriptionRepository;
    private final LiqPayProperties liqPayProperties;
    private final RestClient restClient;
    private final String cartBaseUrl;
    private final String orderBaseUrl;
    private final boolean registerInLiqPay;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               LiqPayProperties liqPayProperties,
                               RestClient restClient,
                               @Value("${services.cart.base-url}") String cartBaseUrl,
                               @Value("${services.order.base-url}") String orderBaseUrl,
                               @Value("${payment.subscription.register-in-liqpay}") boolean registerInLiqPay) {
        this.subscriptionRepository = subscriptionRepository;
        this.liqPayProperties = liqPayProperties;
        this.restClient = restClient;
        this.cartBaseUrl = cartBaseUrl;
        this.orderBaseUrl = orderBaseUrl;
        this.registerInLiqPay = registerInLiqPay;
    }

    @Transactional
    public Subscription subscribe(Long userId, SubscriptionForm form) {
        if (!PERIODICITIES.contains(form.getPeriodicity())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Periodicity must be one of " + PERIODICITIES);
        }
        if (form.getStartDate() == null || form.getStartTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date and time are required");
        }

        CartDTO cart = restClient.get()
                .uri(cartBaseUrl + "/internal/carts/{userId}", userId)
                .retrieve()
                .body(CartDTO.class);

        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot create a subscription from an empty cart");
        }

        Map<Long, Subscription.SubscriptionItem> items = cart.getItems().stream()
                .collect(Collectors.toMap(CartItemDTO::getGoodId,
                        item -> new Subscription.SubscriptionItem(item.getQuantity(), item.getPriceKopeck())));

        // save first: LiqPay needs order_id = subscription.id, and any later failure rolls it back
        Subscription subscription = subscriptionRepository.saveAndFlush(Subscription.builder()
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
            logger.info("LiqPay subscribe skipped for subscription {}: emulation mode", subscription.getId());
        }

        // clearing goes last: a failure rolls the subscription back
        // (checkout-clear keeps the stock reserved — the goods are sold, not returned)
        restClient.post()
                .uri(cartBaseUrl + "/internal/carts/{userId}/checkout-clear", userId)
                .retrieve()
                .toBodilessEntity();

        logger.info("Subscription {} created for user {} ({}, {} items)",
                subscription.getId(), userId, form.getPeriodicity(), items.size());
        return subscription;
    }

    public List<Subscription> getMySubscriptions(Long userId) {
        return subscriptionRepository.findAllByUserIdOrderByIdDesc(userId);
    }

    // a confirmed recurring charge turns the stored snapshot into an order;
    // called by the LiqPay callback and by the local schedule emulator
    public void createOrderFromSubscription(Subscription subscription) {
        List<OrderItemDTO> items = subscription.getItems().entrySet().stream()
                .map(entry -> new OrderItemDTO(entry.getKey(),
                        entry.getValue().getQuantity(),
                        entry.getValue().getPriceKopeck()))
                .toList();

        restClient.post()
                .uri(orderBaseUrl + "/internal/orders/{userId}", subscription.getUserId())
                .body(items)
                .retrieve()
                .toBodilessEntity();

        logger.info("Recurring order created from subscription {} for user {}",
                subscription.getId(), subscription.getUserId());
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
        params.put("description", "Оформлення платіжної підписки, user_id: " + subscription.getUserId());
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
            logger.info("LiqPay subscribe response for subscription {}: {}",
                    subscription.getId(), response);

            Object status = response.get("status");
            if ("error".equals(status) || "failure".equals(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "LiqPay rejected the subscription: " + response.get("err_description"));
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "LiqPay subscribe request failed", e);
        }
    }
}
