package shop.payment.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import shop.payment.model.SupportedCurrency;
import shop.payment.service.OrderGenerator;
import shop.payment.service.PaymentCallbackService;

@RestController
@AllArgsConstructor
public class PaymentController {

  private final OrderGenerator orderGenerator;
  private final PaymentCallbackService paymentCallbackService;

  // called server-side by the client BFF; returns an HTML form to embed on the cart page
  @GetMapping("/payments/form")
  public String paymentForm(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("amount") double amount) {
    if (amount <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
    }
    return orderGenerator.createPaymentFormHtml(currentUserId(jwt), amount, SupportedCurrency.UAH);
  }

  // LiqPay server-to-server callback (server_url); open in SecurityConfig, authenticity is the
  // signature — the empty @SecurityRequirements lifts the global bearer-jwt padlock in OpenAPI
  @SecurityRequirements
  @PostMapping("/payment/new")
  public void paymentCallback(
      @RequestParam("data") String data, @RequestParam("signature") String signature) {
    paymentCallbackService.processPaymentCallback(data, signature);
  }

  // LiqPay callback for recurring subscription charges
  @SecurityRequirements
  @PostMapping("/subscription/payment")
  public void subscriptionCallback(
      @RequestParam("data") String data, @RequestParam("signature") String signature) {
    paymentCallbackService.processSubscriptionCallback(data, signature);
  }

  private Long currentUserId(Jwt jwt) {
    Object uid = jwt.getClaim("uid");
    if (uid instanceof Number number) {
      return number.longValue();
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access token has no uid claim");
  }
}
