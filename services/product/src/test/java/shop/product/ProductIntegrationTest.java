package shop.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import shop.product.model.Good;
import shop.product.model.Manufacturer;
import shop.product.repository.GoodRepository;
import shop.product.repository.ManufacturerRepository;
import shop.product.service.ProductService;

// full-context integration test: real PostgreSQL in a container. Requests carry real
// Bearer headers resolved by a stub JwtDecoder, so the bearer filter, the custom
// JwtAuthenticationConverter and the authorization rules all genuinely execute
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductIntegrationTest {

  private static final String ADMIN = "Bearer admin-token";
  private static final String USER = "Bearer user-token";
  private static final String CART_SERVICE = "Bearer cart-service-token";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private GoodRepository goodRepository;

  @Autowired private ManufacturerRepository manufacturerRepository;

  @Autowired private ProductService productService;

  @BeforeEach
  void cleanUp() {
    goodRepository.deleteAll();
    manufacturerRepository.deleteAll();
  }

  // --- pessimistic locking under concurrency ---

  @Test
  void concurrentReservesCannotOversellTheLastItem() throws Exception {
    long goodId = seedGood(1).getId();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);
      Callable<String> reserveLastItem =
          () -> {
            start.await();
            try {
              productService.reserve(goodId, 1);
              return "reserved";
            } catch (ResponseStatusException e) {
              return e.getStatusCode() == HttpStatus.CONFLICT
                  ? "conflict"
                  : "unexpected " + e.getStatusCode();
            }
          };
      Future<String> first = executor.submit(reserveLastItem);
      Future<String> second = executor.submit(reserveLastItem);
      start.countDown();

      // findWithLockById takes a PESSIMISTIC_WRITE row lock: the loser waits,
      // re-reads quantity 0 and gets 409 — the stock can never go negative
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("reserved", "conflict");
    } finally {
      executor.shutdownNow();
    }

    assertThat(goodRepository.findById(goodId).orElseThrow().getQuantity()).isZero();
  }

  // --- catalog management (custom JwtAuthenticationConverter + ADMIN role) ---

  @Test
  void adminTokenCreatesGoodWithImageAndManufacturers() throws Exception {
    Manufacturer lego =
        manufacturerRepository.saveAndFlush(new Manufacturer("Lego", "lego.com", "bricks"));
    Manufacturer bela =
        manufacturerRepository.saveAndFlush(
            new Manufacturer("Bela", "bela.example.com", "compatible bricks"));
    byte[] imageBytes = "fake-image-bytes".getBytes(StandardCharsets.UTF_8);

    mockMvc
        .perform(
            post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"name":"Конструктор","priceKopeck":129900,"description":"description",
                                 "category":"toys","quantity":7,"imageBase64":"%s","manufacturerIds":[%d,%d]}"""
                        .formatted(
                            Base64.getEncoder().encodeToString(imageBytes),
                            lego.getId(),
                            bela.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Конструктор"))
        .andExpect(jsonPath("$.manufacturers.length()").value(2));

    List<Good> goods = goodRepository.findAll();
    assertThat(goods).hasSize(1);
    assertThat(goods.getFirst().getImage()).isEqualTo(imageBytes);
    // the many-to-many join survived a round-trip through the real schema
    assertThat(goods.getFirst().getManufacturers())
        .extracting(Manufacturer::getName)
        .containsExactlyInAnyOrder("Lego", "Bela");
  }

  @Test
  void invalidCreateGoodRequestIsRejectedWithProblemJson() throws Exception {
    // blank name, negative price and quantity — stopped by Bean Validation at the
    // MVC edge, before ProductService or the database are ever reached
    mockMvc
        .perform(
            post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"  ","priceKopeck":-5,"quantity":-1}"""))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400));

    assertThat(goodRepository.findAll()).isEmpty();
  }

  @Test
  void onlyAdminRoleCanCreateGoods() throws Exception {
    String body =
        """
                {"name":"Конструктор","priceKopeck":129900,"quantity":7,"manufacturerIds":[]}""";

    mockMvc
        .perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    // products.write lets a service move stock, but must not unlock catalog management
    mockMvc
        .perform(
            post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, CART_SERVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    assertThat(goodRepository.findAll()).isEmpty();
  }

  // --- stock endpoints (service scope) ---

  @Test
  void reserveRequiresServiceWriteScope() throws Exception {
    long goodId = seedGood(5).getId();

    mockMvc
        .perform(
            post("/api/products/" + goodId + "/reserve")
                .param("quantity", "2")
                .header(HttpHeaders.AUTHORIZATION, USER))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/products/" + goodId + "/reserve")
                .param("quantity", "2")
                .header(HttpHeaders.AUTHORIZATION, CART_SERVICE))
        .andExpect(status().isOk());

    assertThat(goodRepository.findById(goodId).orElseThrow().getQuantity()).isEqualTo(3);
  }

  // --- catalog reads ---

  @Test
  void catalogReadsRequireReadScope() throws Exception {
    long goodId = seedGood(5).getId();

    mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, USER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(get("/api/products/" + goodId).header(HttpHeaders.AUTHORIZATION, USER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Конструктор"));

    mockMvc
        .perform(get("/api/products/999999").header(HttpHeaders.AUTHORIZATION, USER))
        .andExpect(status().isNotFound());
  }

  // --- helpers ---

  private Good seedGood(int quantity) {
    Good good = new Good("Конструктор", 129900L, "description", "toys", null, List.of());
    good.setQuantity(quantity);
    return goodRepository.saveAndFlush(good);
  }

  // replaces the JWKS-based decoder: maps opaque test tokens to claim sets, so requests
  // go through the real bearer filter and converter instead of a pre-built SecurityContext
  // (the jwt() post-processor would bypass the custom JwtAuthenticationConverter entirely)
  @TestConfiguration
  static class TestTokenConfig {

    @Bean
    JwtDecoder jwtDecoder() {
      return token -> {
        Map<String, Object> claims =
            switch (token) {
              // a real user: SCOPE_* comes from "scope", ROLE_* from "authorities" — the
              // custom converter must merge both for the admin rule to work
              case "admin-token" ->
                  Map.of(
                      "scope", "products.read", "authorities", List.of("ROLE_ADMIN", "ROLE_USER"));
              case "user-token" ->
                  Map.of("scope", "products.read", "authorities", List.of("ROLE_USER"));
              // client_credentials service token: scopes only, no authorities claim
              case "cart-service-token" -> Map.of("scope", "products.read products.write");
              default -> throw new BadJwtException("Unknown test token: " + token);
            };
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
            .header("alg", "none")
            .subject("test-subject")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claims(c -> c.putAll(claims))
            .build();
      };
    }
  }
}
