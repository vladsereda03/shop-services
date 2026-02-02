package shop.client.service;

import com.liqpay.LiqPay;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Service

public class OrderGenerator {

    private final String PUBLIC_KEY;
    private final String PRIVATE_KEY;
    private final RestClient restClient;

    @Autowired
    public OrderGenerator(
            @Value("${liqpay.key.public}") String publicKey,
            @Value("${liqpay.key.private}") String privateKey,
            RestClient restClient) {
        this.PUBLIC_KEY = publicKey;
        this.PRIVATE_KEY = privateKey;
        this.restClient = restClient;
    }

    public String createPaymentFormHtml(HttpSession httpSession, double uah) {
        Map<String, String> params = new HashMap<>();
        params.put("action", "pay");
        params.put("amount", String.valueOf(uah));
        params.put("currency", "UAH");
        params.put("description", "Оплата обраних товарів");
        params.put("order_id", "set_id"); // todo: change logic of passing order_id
        params.put("version", "3");
        params.put("sandbox", "1");
        params.put("result_url", "http://electropoint.hopto.org/order/new");
        params.put("server_url", "http://electropoint.hopto.org/order/payment");


        params.put("info", "no info");
        LiqPay liqpay = new LiqPay(PUBLIC_KEY, PRIVATE_KEY);
        return liqpay.cnb_form(params);
    }
}
