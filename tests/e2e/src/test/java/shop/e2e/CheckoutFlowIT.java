package shop.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * The full cross-service checkout journey against the running stack. It exercises the two real
 * inter-service hops the per-service integration tests stub out at the HTTP level: cart → product
 * (stock reservation) and order → cart (read the cart, then clear it). A good is seeded directly
 * into product's database because no admin user exists to drive the catalog-management API; every
 * other step goes through the public APIs with a real user token from {@link
 * #registerAndLoginUser()}.
 */
class CheckoutFlowIT extends E2eStack {

  @Test
  void addToCartReservesStockThenCheckoutCreatesAnOrderAndClearsTheCart() throws Exception {
    int initialStock = 100;
    int ordered = 3;
    long goodId = seedGood("E2E Widget", 12_300L, initialStock);
    String user = registerAndLoginUser();

    // add to cart → cart calls product to reserve stock (real hop #1)
    HttpResponse<String> added =
        post(cartBaseUrl() + "/carts/my/items?goodId=" + goodId + "&quantity=" + ordered, user);
    assertThat(added.statusCode()).as("addItem: %s", added.body()).isEqualTo(200);
    assertThat(json(added.body()).get("items").size()).isEqualTo(1);

    // the reservation actually decremented product's stock across the service boundary
    HttpResponse<String> good = get(productBaseUrl() + "/api/products/" + goodId, user);
    assertThat(good.statusCode()).isEqualTo(200);
    assertThat(json(good.body()).get("quantity").asInt()).isEqualTo(initialStock - ordered);

    // checkout → order reads the cart and clears it (real hop #2)
    HttpResponse<String> order = checkoutWithRetry(user);
    assertThat(order.statusCode()).as("checkout: %s", order.body()).isEqualTo(200);
    JsonNode orderJson = json(order.body());
    assertThat(orderJson.get("id").asLong()).isPositive();
    assertThat(orderJson.get("items").size()).isEqualTo(1);

    // the order → cart checkout-clear hop emptied the cart
    HttpResponse<String> cartAfter = get(cartBaseUrl() + "/carts/my", user);
    assertThat(json(cartAfter.body()).get("items").size()).isZero();
  }

  // The first order → cart call after a cold start can transiently 5xx while order's OAuth2 client
  // and connection pools warm up. checkout is transactional and rolls back on failure (no order
  // created, cart left intact), so retrying is safe; a 2xx/4xx is returned immediately.
  private static HttpResponse<String> checkoutWithRetry(String user) throws Exception {
    HttpResponse<String> response = null;
    for (int attempt = 1; attempt <= 5; attempt++) {
      response = post(orderBaseUrl() + "/orders/my", user);
      if (response.statusCode() < 500) {
        return response;
      }
      Thread.sleep(1500);
    }
    return response;
  }
}
