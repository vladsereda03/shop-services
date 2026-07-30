package shop.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * First end-to-end smoke against the running Compose stack: auth's OIDC identity holds, a service
 * client can mint a token, and product enforces catalog authorization. These are the same three
 * checks the {@code helm test} hook makes in Kubernetes, here against the Docker Compose deployment
 * artifact and with the neighbours real rather than stubbed.
 */
class CatalogSmokeIT extends E2eStack {

  @Test
  void oidcIssuerIsTheIngressFacingHost() throws Exception {
    HttpResponse<String> response = get(authBaseUrl() + "/.well-known/openid-configuration", null);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"issuer\":\"http://auth.local:9000\"");
  }

  @Test
  void anonymousCatalogIsUnauthorized() throws Exception {
    assertThat(get(productBaseUrl() + "/api/products", null).statusCode()).isEqualTo(401);
  }

  @Test
  void catalogWithServiceTokenIsOk() throws Exception {
    String token = serviceToken("cart-service", "cart-service-secret", "products.read");
    HttpResponse<String> response = get(productBaseUrl() + "/api/products", token);
    assertThat(response.statusCode()).isEqualTo(200);
  }
}
