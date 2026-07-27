package shop.client.controller;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import reactor.core.publisher.Flux;
import shop.client.dto.GoodDTO;
import shop.client.dto.OrderDTO;

@Controller
public class OrderController {

  // rows shown on the orders page: order items enriched with product names
  public record OrderItemView(String name, Integer quantity) {}

  public record OrderView(Long id, String createdAt, double totalGrn, List<OrderItemView> items) {}

  private static final DateTimeFormatter CREATED_AT_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

  private final RestClient restClient;
  private final WebClient webClient;
  private final String orderBaseUrl;
  private final String productBaseUrl;

  public OrderController(
      RestClient restClient,
      WebClient webClient,
      @Value("${services.order.base-url}") String orderBaseUrl,
      @Value("${services.product.base-url}") String productBaseUrl) {
    this.restClient = restClient;
    this.webClient = webClient;
    this.orderBaseUrl = orderBaseUrl;
    this.productBaseUrl = productBaseUrl;
  }

  @GetMapping("/orders")
  public String myOrders(Model model) {
    List<OrderDTO> orders =
        restClient
            .get()
            .uri(orderBaseUrl + "/orders/my")
            .retrieve()
            .body(new ParameterizedTypeReference<List<OrderDTO>>() {});

    Map<Long, GoodDTO> goodsById =
        restClient
            .get()
            .uri(productBaseUrl + "/api/products")
            .retrieve()
            .body(new ParameterizedTypeReference<List<GoodDTO>>() {})
            .stream()
            .collect(Collectors.toMap(GoodDTO::getId, Function.identity()));

    List<OrderView> views =
        orders.stream()
            .map(
                order ->
                    new OrderView(
                        order.getId(),
                        CREATED_AT_FORMAT.format(order.getCreatedAt()),
                        order.getTotalPrice(),
                        order.getItems().stream()
                            .map(
                                item ->
                                    new OrderItemView(
                                        goodsById.containsKey(item.getGoodId())
                                            ? goodsById.get(item.getGoodId()).getName()
                                            : "Товар #" + item.getGoodId(),
                                        item.getQuantity()))
                            .toList()))
            .toList();

    model.addAttribute("orders", views);
    return "order/all_orders";
  }

  // browser-facing SSE relay: streams the user's live-orders feed from the order service and
  // re-emits it to the browser (which consumes it with EventSource). The access token is captured
  // here, on the request thread where the security context is available, and passed as a static
  // bearer header — so the reactive relay does not depend on thread-bound context. The token never
  // reaches the browser (BFF pattern intact); the browser is authenticated by its session cookie.
  @GetMapping(value = "/orders/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @ResponseBody
  public Flux<OrderDTO> stream(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient client) {
    String token = client.getAccessToken().getTokenValue();
    return webClient
        .get()
        .uri(orderBaseUrl + "/orders/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .headers(headers -> headers.setBearerAuth(token))
        .retrieve()
        .bodyToFlux(OrderDTO.class);
  }

  @GetMapping("/orders/checkout")
  public String checkout(RedirectAttributes redirectAttributes) {
    try {
      restClient.post().uri(orderBaseUrl + "/orders/my").retrieve().toBodilessEntity();
    } catch (HttpClientErrorException e) {
      redirectAttributes.addFlashAttribute(
          "orderError",
          e.getStatusCode().value() == 400
              ? "Кошик порожній — нема чого замовляти"
              : "Не вдалося оформити замовлення");
      return "redirect:/cart";
    }
    return "redirect:/orders";
  }
}
