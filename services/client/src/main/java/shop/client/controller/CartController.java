package shop.client.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shop.client.dto.CartDTO;
import shop.client.dto.GoodDTO;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/cart")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    // row shown on the cart page: cart item enriched with the product name
    public record CartItemView(Long goodId, String name, Integer quantity, double totalGrn) {}

    private final RestClient restClient;
    private final String cartBaseUrl;
    private final String productBaseUrl;
    private final String paymentBaseUrl;

    public CartController(RestClient restClient,
                          @Value("${services.cart.base-url}") String cartBaseUrl,
                          @Value("${services.product.base-url}") String productBaseUrl,
                          @Value("${services.payment.base-url}") String paymentBaseUrl) {
        this.restClient = restClient;
        this.cartBaseUrl = cartBaseUrl;
        this.productBaseUrl = productBaseUrl;
        this.paymentBaseUrl = paymentBaseUrl;
    }

    @GetMapping()
    public String showCart(Model model) {
        CartDTO cart = restClient.get()
                .uri(cartBaseUrl + "/carts/my")
                .retrieve()
                .body(CartDTO.class);

        Map<Long, GoodDTO> goodsById = restClient.get()
                .uri(productBaseUrl + "/api/products")
                .retrieve()
                .body(new ParameterizedTypeReference<List<GoodDTO>>() {})
                .stream()
                .collect(Collectors.toMap(GoodDTO::getId, Function.identity()));

        List<CartItemView> items = cart.getItems().stream()
                .map(item -> new CartItemView(
                        item.getGoodId(),
                        goodsById.containsKey(item.getGoodId())
                                ? goodsById.get(item.getGoodId()).getName()
                                : "Товар #" + item.getGoodId(),
                        item.getQuantity(),
                        item.getPriceKopeck() * item.getQuantity() / 100.0))
                .toList();

        model.addAttribute("items", items);
        model.addAttribute("price", cart.getTotalPrice());

        // LiqPay form is rendered by the payment service; without it the page still works
        if (!items.isEmpty()) {
            try {
                String liqpayForm = restClient.get()
                        .uri(paymentBaseUrl + "/payments/form?amount={amount}", cart.getTotalPrice())
                        .retrieve()
                        .body(String.class);
                model.addAttribute("liqpayForm", liqpayForm);
            } catch (RestClientException e) {
                logger.warn("Payment service is unavailable, cart page is shown without the LiqPay form", e);
            }
        }

        return "cart/cart";
    }

    @GetMapping("/add")
    public String addToCart(@RequestParam("goodId") Long goodId,
                            @RequestParam("quantity") int quantity,
                            RedirectAttributes redirectAttributes) {
        try {
            restClient.post()
                    .uri(cartBaseUrl + "/carts/my/items?goodId={goodId}&quantity={quantity}", goodId, quantity)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            redirectAttributes.addFlashAttribute("cartError",
                    e.getStatusCode().value() == 409
                            ? "Недостатньо товару на складі"
                            : "Не вдалося додати товар у кошик");
            return "redirect:/goods/" + goodId;
        }
        return "redirect:/";
    }

    @GetMapping("/clear")
    public String clearCart() {
        restClient.delete()
                .uri(cartBaseUrl + "/carts/my")
                .retrieve()
                .toBodilessEntity();
        return "redirect:/";
    }
}
