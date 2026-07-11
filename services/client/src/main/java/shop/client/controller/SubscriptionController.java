package shop.client.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shop.client.dto.CartDTO;
import shop.client.dto.SubscriptionDTO;
import shop.client.dto.SubscriptionForm;

@Controller
public class SubscriptionController {

  private final RestClient restClient;
  private final String cartBaseUrl;
  private final String paymentBaseUrl;

  public SubscriptionController(
      RestClient restClient,
      @Value("${services.cart.base-url}") String cartBaseUrl,
      @Value("${services.payment.base-url}") String paymentBaseUrl) {
    this.restClient = restClient;
    this.cartBaseUrl = cartBaseUrl;
    this.paymentBaseUrl = paymentBaseUrl;
  }

  @GetMapping("/subscription/new")
  public String showSubscriptionForm(Model model, RedirectAttributes redirectAttributes) {
    CartDTO cart = restClient.get().uri(cartBaseUrl + "/carts/my").retrieve().body(CartDTO.class);

    if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
      redirectAttributes.addFlashAttribute(
          "orderError", "Кошик порожній — додайте товари перед оформленням підписки");
      return "redirect:/cart";
    }

    model.addAttribute("subscriptionPrice", cart.getTotalPrice());
    model.addAttribute("subscriptionCurrency", "UAH");
    model.addAttribute("subscriptionForm", new SubscriptionForm());

    return "payment/subscription";
  }

  @PostMapping("/subscription")
  public String createSubscription(
      @ModelAttribute SubscriptionForm form, RedirectAttributes redirectAttributes) {
    try {
      SubscriptionDTO subscription =
          restClient
              .post()
              .uri(paymentBaseUrl + "/subscriptions/my")
              .body(form)
              .retrieve()
              .body(SubscriptionDTO.class);

      redirectAttributes.addFlashAttribute(
          "subscriptionSuccess",
          "Підписку №"
              + subscription.getId()
              + " оформлено, перше списання: "
              + subscription.getStartDate());
      return "redirect:/cart";
    } catch (HttpClientErrorException e) {
      // 400: empty cart or invalid form data
      redirectAttributes.addFlashAttribute("orderError", "Не вдалося оформити підписку");
      return "redirect:/cart";
    } catch (HttpServerErrorException e) {
      // 502: LiqPay rejected the subscribe request
      redirectAttributes.addFlashAttribute(
          "orderError", "Платіжний сервіс відхилив підписку, спробуйте пізніше");
      return "redirect:/cart";
    }
  }
}
