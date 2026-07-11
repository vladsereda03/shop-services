package shop.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import shop.order.model.Order;
import shop.order.repository.OrderRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// full-context integration test: real PostgreSQL in a container,
// cart-service is the only stubbed piece (its OAuth2 RestClient is replaced below)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderIntegrationTest {

    private static final String CART_BASE_URL = "http://cart.local:8083";
    private static final long USER_ID = 42L;

    private static final String CART_WITH_TWO_ITEMS = """
            {"id":1,"userId":42,"items":[
              {"goodId":5,"quantity":2,"priceKopeck":1500},
              {"goodId":6,"quantity":1,"priceKopeck":500}
            ],"totalPrice":35.0}""";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MockRestServiceServer cartServer;

    @BeforeEach
    void cleanUp() {
        cartServer.reset();
        orderRepository.deleteAll();
    }

    // --- checkout ---

    @Test
    void checkoutTurnsCartIntoOrderAndClearsCartLast() throws Exception {
        cartServer.expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(CART_WITH_TWO_ITEMS, MediaType.APPLICATION_JSON));
        cartServer.expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID + "/checkout-clear"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        mockMvc.perform(post("/orders/my")
                        .with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalPrice").value(35.0));

        cartServer.verify();
        List<Order> orders = orderRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID);
        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().getItems().get(5L).getQuantity()).isEqualTo(2);
        assertThat(orders.getFirst().getItems().get(6L).getPriceKopeck()).isEqualTo(500L);
    }

    @Test
    void failedCartClearRollsBackTheOrder() throws Exception {
        cartServer.expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(CART_WITH_TWO_ITEMS, MediaType.APPLICATION_JSON));
        cartServer.expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID + "/checkout-clear"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        mockMvc.perform(post("/orders/my")
                        .with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
                .andExpect(status().isConflict());

        // clearing goes last inside the transaction: its failure must undo the flushed order
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void emptyCartCheckoutIsRejectedWith400() throws Exception {
        cartServer.expect(requestTo(CART_BASE_URL + "/internal/carts/" + USER_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":1,\"userId\":42,\"items\":[],\"totalPrice\":0.0}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/orders/my")
                        .with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
                .andExpect(status().isBadRequest());

        cartServer.verify(); // checkout-clear must not have been called
        assertThat(orderRepository.findAll()).isEmpty();
    }

    // --- order history ---

    @Test
    void myOrdersReturnsOnlyOwnOrdersNewestFirst() throws Exception {
        Order older = orderRepository.saveAndFlush(orderOf(USER_ID,
                Instant.now().minusSeconds(3600)));
        Order newer = orderRepository.saveAndFlush(orderOf(USER_ID, Instant.now()));
        orderRepository.saveAndFlush(orderOf(77L, Instant.now()));

        mockMvc.perform(get("/orders/my")
                        .with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer.getId().intValue()))
                .andExpect(jsonPath("$[1].id").value(older.getId().intValue()));
    }

    // --- internal API (payment service) ---

    @Test
    void internalApiRequiresOrdersWriteScope() throws Exception {
        // a plain user token must not reach the internal API
        mockMvc.perform(post("/internal/orders/" + USER_ID + "/checkout")
                        .with(jwt().jwt(jwt -> jwt.claim("uid", USER_ID))))
                .andExpect(status().isForbidden());
    }

    @Test
    void subscriptionChargeCreatesOrderBypassingCart() throws Exception {
        mockMvc.perform(post("/internal/orders/" + USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"goodId\":5,\"quantity\":2,\"priceKopeck\":1500}]")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.write"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(30.0));

        cartServer.verify(); // the snapshot path must not touch cart-service
        List<Order> orders = orderRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID);
        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().getItems().get(5L).getQuantity()).isEqualTo(2);
    }

    // --- helpers ---

    private static Order orderOf(long userId, Instant createdAt) {
        return Order.builder()
                .userId(userId)
                .createdAt(createdAt)
                .items(Map.of(5L, new Order.OrderItem(1, 1000L)))
                .build();
    }

    // replaces the OAuth2-interceptor RestClient from RestClientConfig: tests must not
    // fetch client-credentials tokens, and cart-service answers come from the mock server
    @TestConfiguration
    static class CartClientStubConfig {

        @Bean
        RestClient.Builder cartStubRestClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer cartServer(RestClient.Builder cartStubRestClientBuilder) {
            return MockRestServiceServer.bindTo(cartStubRestClientBuilder).build();
        }

        @Bean
        @Primary
        RestClient cartStubRestClient(RestClient.Builder cartStubRestClientBuilder,
                                      MockRestServiceServer cartServer) {
            return cartStubRestClientBuilder.build();
        }
    }
}
