package shop.client.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shop.client.dto.GoodDTO;
import shop.client.dto.OrderDTO;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class OrderController {

    // rows shown on the orders page: order items enriched with product names
    public record OrderItemView(String name, Integer quantity) {}
    public record OrderView(Long id, String createdAt, double totalGrn, List<OrderItemView> items) {}

    private static final DateTimeFormatter CREATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final RestClient restClient;
    private final String orderBaseUrl;
    private final String productBaseUrl;

    public OrderController(RestClient restClient,
                           @Value("${services.order.base-url}") String orderBaseUrl,
                           @Value("${services.product.base-url}") String productBaseUrl) {
        this.restClient = restClient;
        this.orderBaseUrl = orderBaseUrl;
        this.productBaseUrl = productBaseUrl;
    }

    @GetMapping("/orders")
    public String myOrders(Model model) {
        List<OrderDTO> orders = restClient.get()
                .uri(orderBaseUrl + "/orders/my")
                .retrieve()
                .body(new ParameterizedTypeReference<List<OrderDTO>>() {});

        Map<Long, GoodDTO> goodsById = restClient.get()
                .uri(productBaseUrl + "/api/products")
                .retrieve()
                .body(new ParameterizedTypeReference<List<GoodDTO>>() {})
                .stream()
                .collect(Collectors.toMap(GoodDTO::getId, Function.identity()));

        List<OrderView> views = orders.stream()
                .map(order -> new OrderView(
                        order.getId(),
                        CREATED_AT_FORMAT.format(order.getCreatedAt()),
                        order.getTotalPrice(),
                        order.getItems().stream()
                                .map(item -> new OrderItemView(
                                        goodsById.containsKey(item.getGoodId())
                                                ? goodsById.get(item.getGoodId()).getName()
                                                : "Товар #" + item.getGoodId(),
                                        item.getQuantity()))
                                .toList()))
                .toList();

        model.addAttribute("orders", views);
        return "order/all_orders";
    }

    @GetMapping("/orders/checkout")
    public String checkout(RedirectAttributes redirectAttributes) {
        try {
            restClient.post()
                    .uri(orderBaseUrl + "/orders/my")
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            redirectAttributes.addFlashAttribute("orderError",
                    e.getStatusCode().value() == 400
                            ? "Кошик порожній — нема чого замовляти"
                            : "Не вдалося оформити замовлення");
            return "redirect:/cart";
        }
        return "redirect:/orders";
    }
}
