package shop.e2e;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Shared Docker Compose stack for the end-to-end acceptance tests. Started once for the whole
 * module (the Testcontainers singleton-container pattern) and reaped by Ryuk at JVM exit, so every
 * {@code *IT} in the module runs against the same running stack instead of paying the startup cost
 * per class.
 *
 * <p>The stack is the real built service images ({@code shop/<svc>:latest}) over real Postgres,
 * Kafka, Redis and MinIO — the cross-service paths the per-service integration tests stub out. It
 * signs tokens with an ephemeral RSA key pair generated here and injected into auth, so no secret
 * is needed to run the suite. Auth's OIDC issuer stays {@code http://auth.local:9000}; product
 * resolves that host to the auth container through a compose network alias (see compose.e2e.yaml),
 * while the test itself reaches the services on Testcontainers' mapped ports.
 */
abstract class E2eStack {

  static final ComposeContainer STACK;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  static {
    String[] keys = ephemeralRsaKeyPair();
    STACK =
        new ComposeContainer(new File("src/test/resources/compose.e2e.yaml"))
            // real docker networking (the auth.local alias) and reuse of the local service images,
            // exactly like a developer running `docker compose up`
            .withLocalCompose(true)
            .withEnv("JWK_KEY_ID", "e2e-key")
            .withEnv("JWT_PUBLIC_KEY", keys[0])
            .withEnv("JWT_PRIVATE_KEY", keys[1])
            .withExposedService(
                "auth",
                9000,
                Wait.forHttp("/actuator/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(4)))
            .withExposedService(
                "product",
                8082,
                Wait.forHttp("/actuator/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(4)))
            .withExposedService(
                "cart",
                8083,
                Wait.forHttp("/actuator/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(4)))
            .withExposedService(
                "order",
                8084,
                Wait.forHttp("/actuator/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(4)))
            // exposed for JDBC seeding of the products DB (no admin user to use the catalog API)
            .withExposedService("postgres", 5432, Wait.forListeningPort());
    STACK.start();
  }

  static String authBaseUrl() {
    return "http://"
        + STACK.getServiceHost("auth", 9000)
        + ":"
        + STACK.getServicePort("auth", 9000);
  }

  static String productBaseUrl() {
    return "http://"
        + STACK.getServiceHost("product", 8082)
        + ":"
        + STACK.getServicePort("product", 8082);
  }

  static String cartBaseUrl() {
    return "http://"
        + STACK.getServiceHost("cart", 8083)
        + ":"
        + STACK.getServicePort("cart", 8083);
  }

  static String orderBaseUrl() {
    return "http://"
        + STACK.getServiceHost("order", 8084)
        + ":"
        + STACK.getServicePort("order", 8084);
  }

  /** Fetches a client_credentials access token from auth for the given service client and scope. */
  static String serviceToken(String clientId, String clientSecret, String scope) throws Exception {
    String basic =
        Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(UTF_8));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(authBaseUrl() + "/oauth2/token"))
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString("grant_type=client_credentials&scope=" + scope))
            .build();
    HttpResponse<String> response = HTTP.send(request, BodyHandlers.ofString());
    assertThat(response.statusCode()).as("token response: %s", response.body()).isEqualTo(200);
    return JSON.readTree(response.body()).get("access_token").asText();
  }

  /** GET {@code url}, optionally bearer-authenticated (pass {@code null} for anonymous). */
  static HttpResponse<String> get(String url, String bearerToken) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
    if (bearerToken != null) {
      request.header("Authorization", "Bearer " + bearerToken);
    }
    return HTTP.send(request.build(), BodyHandlers.ofString());
  }

  /**
   * Bodiless bearer-authenticated POST (cart add-item and checkout carry their args in the query).
   */
  static HttpResponse<String> post(String url, String bearerToken) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + bearerToken)
            .POST(BodyPublishers.noBody())
            .build();
    return HTTP.send(request, BodyHandlers.ofString());
  }

  static JsonNode json(String body) throws Exception {
    return JSON.readTree(body);
  }

  /**
   * Seeds a catalog good with stock straight into product's database and returns its generated id.
   * There is no admin user to drive the catalog-management API, so the fixture goes in over JDBC on
   * the exposed postgres port; only the two NOT NULL columns (price, quantity) plus a name matter.
   */
  static long seedGood(String name, long priceKopeck, int quantity) throws Exception {
    String jdbcUrl =
        "jdbc:postgresql://"
            + STACK.getServiceHost("postgres", 5432)
            + ":"
            + STACK.getServicePort("postgres", 5432)
            + "/products";
    try (Connection connection = DriverManager.getConnection(jdbcUrl, "shop", "shop");
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO good (name, description, category, price_kopeck, quantity)"
                    + " VALUES (?, ?, ?, ?, ?) RETURNING id")) {
      statement.setString(1, name);
      statement.setString(2, "e2e seeded good");
      statement.setString(3, "e2e");
      statement.setLong(4, priceKopeck);
      statement.setInt(5, quantity);
      try (ResultSet keys = statement.executeQuery()) {
        keys.next();
        return keys.getLong(1);
      }
    }
  }

  /**
   * Registers a fresh user through auth's signup form and completes an OIDC authorization_code
   * login for the {@code client} application, returning that user's access token. Only a user token
   * carries the {@code uid} claim the resource servers key users on (client_credentials tokens do
   * not), so this is the way to drive the user-facing cart/order APIs. The redirect URI is never
   * followed — the BFF is not in this stack — so the code is read straight off auth's 302 Location.
   */
  static String registerAndLoginUser() throws Exception {
    // a session-scoped client (cookie jar) that does not auto-follow redirects, so the login flow
    // can carry the JSESSIONID across steps and capture the authorization code from the 302
    HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    String auth = authBaseUrl();
    String redirectUri = "http://localhost:8080/login/oauth2/code/client";

    SecureRandom random = new SecureRandom();
    String suffix = String.format("%09d", Math.floorMod(random.nextLong(), 1_000_000_000L));
    String username = "e2euser" + suffix; // matches ^[a-zA-Z][a-zA-Z0-9_]*$, length >= 8
    String password = "e2ePassw0rd";
    String email = "e2e" + suffix + "@example.com";
    String phone = "+380" + String.format("%09d", Math.floorMod(random.nextLong(), 1_000_000_000L));

    // 1. GET the signup form: seeds the session cookie and the CSRF token
    HttpResponse<String> form =
        client.send(
            HttpRequest.newBuilder(URI.create(auth + "/account/signup/form")).GET().build(),
            BodyHandlers.ofString());
    String csrf = firstGroup(form.body(), "name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    // 2. POST signup: creates the USER-role user and authenticates the session (auth does this
    // inline)
    String signupBody =
        formEncoded(
            "username", username,
            "password", password,
            "email", email,
            "fullName", "E2E User",
            "phone", phone,
            "_csrf", csrf);
    HttpResponse<String> signup =
        client.send(
            HttpRequest.newBuilder(URI.create(auth + "/account/signup"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(signupBody))
                .build(),
            BodyHandlers.ofString());
    assertThat(signup.statusCode())
        .as("signup should redirect, got %s: %s", signup.statusCode(), signup.body())
        .isEqualTo(302);

    // 3. GET authorize on the authenticated session: consent and PKCE are off -> straight 302 with
    // code
    String authorizeUrl =
        auth
            + "/oauth2/authorize?response_type=code&client_id=client&state=e2e&redirect_uri="
            + enc(redirectUri)
            + "&scope="
            + enc("openid products.read");
    HttpResponse<String> authorize =
        client.send(
            HttpRequest.newBuilder(URI.create(authorizeUrl)).GET().build(),
            BodyHandlers.ofString());
    String location = authorize.headers().firstValue("Location").orElse("");
    assertThat(location)
        .as(
            "authorize should redirect to the client with a code, got %s -> %s",
            authorize.statusCode(), location)
        .contains("code=");
    String code = firstGroup(location, "[?&]code=([^&]+)");

    // 4. exchange the code for tokens (client secret auth); return the access token
    String basic = Base64.getEncoder().encodeToString("client:secret".getBytes(UTF_8));
    HttpResponse<String> token =
        client.send(
            HttpRequest.newBuilder(URI.create(auth + "/oauth2/token"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    BodyPublishers.ofString(
                        "grant_type=authorization_code&code="
                            + code
                            + "&redirect_uri="
                            + enc(redirectUri)))
                .build(),
            BodyHandlers.ofString());
    assertThat(token.statusCode()).as("token response: %s", token.body()).isEqualTo(200);
    return JSON.readTree(token.body()).get("access_token").asText();
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, UTF_8);
  }

  private static String formEncoded(String... keyValues) {
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < keyValues.length; i += 2) {
      if (i > 0) {
        body.append('&');
      }
      body.append(enc(keyValues[i])).append('=').append(enc(keyValues[i + 1]));
    }
    return body.toString();
  }

  private static String firstGroup(String haystack, String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(haystack);
    if (!matcher.find()) {
      throw new IllegalStateException("pattern /" + regex + "/ not found in: " + haystack);
    }
    return matcher.group(1);
  }

  private static String[] ephemeralRsaKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      // auth decodes JWT_PUBLIC_KEY with X509EncodedKeySpec (SPKI) and JWT_PRIVATE_KEY with
      // PKCS8EncodedKeySpec — precisely what getEncoded() yields for each; Base64, no line breaks
      return new String[] {
        Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
        Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
      };
    } catch (Exception e) {
      throw new IllegalStateException("failed to generate the e2e RSA key pair", e);
    }
  }
}
