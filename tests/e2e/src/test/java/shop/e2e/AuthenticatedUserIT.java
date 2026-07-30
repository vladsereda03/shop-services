package shop.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * Exercises the user-facing path with a real OIDC authorization_code login. Cart's {@code
 * /carts/my} keys on the token's {@code uid} claim, which only a user token carries — a
 * client_credentials token cannot reach it. Anonymous access is rejected; the logged-in user gets
 * their auto-created, empty cart. This is the login capability the full checkout scenario builds
 * on.
 */
class AuthenticatedUserIT extends E2eStack {

  @Test
  void anonymousCartIsUnauthorized() throws Exception {
    assertThat(get(cartBaseUrl() + "/carts/my", null).statusCode()).isEqualTo(401);
  }

  @Test
  void loggedInUserGetsTheirEmptyCart() throws Exception {
    String userToken = registerAndLoginUser();
    HttpResponse<String> response = get(cartBaseUrl() + "/carts/my", userToken);
    assertThat(response.statusCode()).as("cart response: %s", response.body()).isEqualTo(200);
    // a freshly provisioned cart carries the caller's uid and an empty item list
    assertThat(response.body()).contains("\"userId\"").contains("\"items\":[]");
  }
}
