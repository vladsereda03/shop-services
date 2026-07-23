package shop.payment.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import shop.payment.model.dto.SubscriptionDTO;
import shop.payment.model.dto.SubscriptionForm;
import shop.payment.service.SubscriptionService;

@RestController
@AllArgsConstructor
@RequestMapping("/subscriptions")
public class SubscriptionController {

  private final SubscriptionService subscriptionService;

  // subscribe the current cart: called server-side by the client BFF
  @PostMapping("/my")
  public SubscriptionDTO subscribe(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubscriptionForm form) {
    return SubscriptionDTO.from(subscriptionService.subscribe(currentUserId(jwt), form));
  }

  @GetMapping("/my")
  public List<SubscriptionDTO> mySubscriptions(@AuthenticationPrincipal Jwt jwt) {
    return subscriptionService.getMySubscriptions(currentUserId(jwt)).stream()
        .map(SubscriptionDTO::from)
        .toList();
  }

  // soft-cancel: the scheduler stops charging the subscription; only the owner may cancel
  @PostMapping("/my/{id}/cancel")
  public SubscriptionDTO cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return SubscriptionDTO.from(subscriptionService.cancel(currentUserId(jwt), id));
  }

  private Long currentUserId(Jwt jwt) {
    Object uid = jwt.getClaim("uid");
    if (uid instanceof Number number) {
      return number.longValue();
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access token has no uid claim");
  }
}
