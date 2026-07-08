package shop.payment.service;

import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import shop.payment.config.LiqPayProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCallbackService.class);

    // statuses that mean the money is (test-)charged; anything else is not a completed payment
    private static final Set<String> PAID_STATUSES = Set.of("success", "sandbox");

    private final LiqPayProperties liqPayProperties;
    private final RestClient restClient;

    @Value("${services.order.base-url}")
    private String orderBaseUrl;

    public void processPaymentCallback(String data, String signature) {
        // the endpoint is open (LiqPay has no our JWT), so the signature is the only authenticity proof
        if (!expectedSignature(data).equals(signature)) {
            logger.warn("LiqPay callback rejected: signature mismatch");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid LiqPay signature");
        }

        JSONObject payload = new JSONObject(new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8));
        String status = payload.optString("status");
        String orderId = payload.optString("order_id");

        if (!PAID_STATUSES.contains(status)) {
            logger.info("LiqPay callback for order_id {} ignored: status '{}' is not a completed payment",
                    orderId, status);
            return;
        }

        long userId = Long.parseLong(payload.getString("info"));

        try {
            restClient.post()
                    .uri(orderBaseUrl + "/internal/orders/{userId}/checkout", userId)
                    .retrieve()
                    .toBodilessEntity();
            logger.info("LiqPay payment {} confirmed: order created for user {}", orderId, userId);
        } catch (HttpClientErrorException.BadRequest e) {
            // cart is already empty — most likely a duplicate callback; answer 200 so LiqPay stops retrying
            logger.warn("LiqPay callback for user {} skipped: cart is empty (duplicate callback?)", userId);
        }
    }

    // LiqPay signature formula: base64(sha1(private_key + data + private_key))
    private String expectedSignature(String data) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(
                    (liqPayProperties.privateKey() + data + liqPayProperties.privateKey())
                            .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }
}
