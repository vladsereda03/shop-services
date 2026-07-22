package shop.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import shop.payment.client.OrderClient;
import shop.payment.config.LiqPayProperties;
import shop.payment.model.ProcessedCallback;
import shop.payment.model.Subscription;
import shop.payment.repository.ProcessedCallbackRepository;
import shop.payment.repository.SubscriptionRepository;

@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentCallbackService.class);

  // statuses that mean the money is (test-)charged; anything else is not a completed payment
  private static final Set<String> PAID_STATUSES = Set.of("success", "sandbox");

  private final LiqPayProperties liqPayProperties;
  private final OrderClient orderClient;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionService subscriptionService;
  private final ProcessedCallbackRepository processedCallbackRepository;

  // transactional so the processed-callback row commits or rolls back together with the
  // downstream call: a failed checkout leaves no row and a LiqPay retry can reprocess
  @Transactional
  public void processPaymentCallback(String data, String signature) {
    // the endpoint is open (LiqPay has no our JWT), so the signature is the only authenticity proof
    if (!expectedSignature(data).equals(signature)) {
      logger.warn("LiqPay callback rejected: signature mismatch");
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid LiqPay signature");
    }

    JSONObject payload =
        new JSONObject(new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8));
    String status = payload.optString("status");
    String orderId = payload.optString("order_id");

    if (!PAID_STATUSES.contains(status)) {
      logger.info(
          "LiqPay callback for order_id {} ignored: status '{}' is not a completed payment",
          orderId,
          status);
      return;
    }

    long userId = Long.parseLong(payload.getString("info"));
    long paymentId = payload.optLong("payment_id", -1);

    if (alreadyProcessed(paymentId)) {
      return;
    }

    try {
      // the payment_id also travels to order-service as the idempotency key, so a callback
      // redelivered after this transaction committed but before the processed-callback row did
      // still resolves to the same order instead of a duplicate
      orderClient.checkout(userId, paymentId > 0 ? paymentId : null);
      logger.info("LiqPay payment {} confirmed: order created for user {}", orderId, userId);
    } catch (HttpClientErrorException.BadRequest e) {
      // cart is already empty — most likely a duplicate callback; answer 200 so LiqPay stops
      // retrying
      logger.warn(
          "LiqPay callback for user {} skipped: cart is empty (duplicate callback?)", userId);
    }
  }

  // recurring subscription charge: LiqPay debited the card, we turn the snapshot into an order
  @Transactional
  public void processSubscriptionCallback(String data, String signature) {
    if (!expectedSignature(data).equals(signature)) {
      logger.warn("LiqPay subscription callback rejected: signature mismatch");
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid LiqPay signature");
    }

    JSONObject payload =
        new JSONObject(new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8));
    String status = payload.optString("status");
    String orderId = payload.optString("order_id");

    if (!PAID_STATUSES.contains(status)) {
      // e.g. "subscribed" (subscription registered) or a failed charge — nothing to ship
      logger.info(
          "LiqPay subscription callback for order_id {} ignored: status '{}'", orderId, status);
      return;
    }

    long subscriptionId;
    try {
      subscriptionId = Long.parseLong(orderId);
    } catch (NumberFormatException e) {
      logger.warn(
          "LiqPay subscription callback ignored: order_id '{}' is not a subscription id", orderId);
      return;
    }

    Subscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
    if (subscription == null) {
      // charge for a subscription we don't have (e.g. rolled back at subscribe time)
      logger.warn(
          "LiqPay subscription callback ignored: subscription {} not found", subscriptionId);
      return;
    }

    long paymentId = payload.optLong("payment_id", -1);
    if (alreadyProcessed(paymentId)) {
      return;
    }

    subscriptionService.createOrderFromSubscription(subscription, paymentId > 0 ? paymentId : null);
    logger.info("LiqPay recurring payment confirmed for subscription {}", subscriptionId);
  }

  // deduplication by LiqPay's payment_id (unique per charge). Insert-FIRST, before the
  // downstream call: a concurrent duplicate blocks on the primary key at flush and fails
  // before it can produce a second order, so the effect runs at most once per payment_id
  private boolean alreadyProcessed(long paymentId) {
    if (paymentId <= 0) {
      logger.warn("LiqPay callback carries no payment_id — processed without deduplication");
      return false;
    }
    if (processedCallbackRepository.existsById(paymentId)) {
      logger.info("LiqPay callback {} already processed — duplicate ignored", paymentId);
      return true;
    }
    processedCallbackRepository.saveAndFlush(new ProcessedCallback(paymentId));
    return false;
  }

  // LiqPay signature formula: base64(sha1(private_key + data + private_key))
  private String expectedSignature(String data) {
    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      byte[] digest =
          sha1.digest(
              (liqPayProperties.privateKey() + data + liqPayProperties.privateKey())
                  .getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 is not available", e);
    }
  }
}
