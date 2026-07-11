package shop.payment.service;

import com.liqpay.LiqPay;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import shop.payment.config.LiqPayProperties;
import shop.payment.model.SupportedCurrency;

@Service
public class OrderGenerator {

  private final LiqPayProperties liqPayProperties;
  private final String resultUrl;
  private final String serverUrl;

  public OrderGenerator(
      LiqPayProperties liqPayProperties,
      @Value("${payment.result-url}") String resultUrl,
      @Value("${payment.server-url}") String serverUrl) {
    this.liqPayProperties = liqPayProperties;
    this.resultUrl = resultUrl;
    this.serverUrl = serverUrl;
  }

  public String createPaymentFormHtml(Long userId, double amount, SupportedCurrency currency) {
    Map<String, String> params = new HashMap<>();
    params.put("version", "7");
    params.put("sandbox", "1");

    params.put("action", "pay");
    params.put("amount", String.valueOf(amount));
    params.put("currency", currency.getCode());
    params.put("description", "Оплата обраних товарів");
    // LiqPay requires a unique order_id before the shop order exists;
    // the real order is created in the callback, the user is found by "info"
    params.put("order_id", UUID.randomUUID().toString());
    params.put("result_url", resultUrl);
    params.put("server_url", serverUrl);
    params.put("info", String.valueOf(userId));

    LiqPay liqpay = new LiqPay(liqPayProperties.publicKey(), liqPayProperties.privateKey());
    return liqpay.cnb_form(params);
  }
}
