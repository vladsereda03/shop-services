package shop.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import shop.auth.model.User;
import shop.auth.repository.UserRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final UserRepository userRepository;

  private final String JWK_KEY_ID;
  private final String JWT_PUBLIC_KEY;
  private final String JWT_PRIVATE_KEY;

  public SecurityConfig(
      UserRepository userRepository,
      @Value("${jwk.key-id}") String JWK_KEY_ID,
      @Value("${jwt.public-key}") String JWT_PUBLIC_KEY,
      @Value("${jwt.private-key}") String JWT_PRIVATE_KEY) {
    this.userRepository = userRepository;
    this.JWK_KEY_ID = JWK_KEY_ID;
    this.JWT_PUBLIC_KEY = JWT_PUBLIC_KEY;
    this.JWT_PRIVATE_KEY = JWT_PRIVATE_KEY;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
      throws Exception {
    OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
        OAuth2AuthorizationServerConfigurer.authorizationServer();

    http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
        .with(
            authorizationServerConfigurer,
            (authorizationServer) ->
                authorizationServer.oidc(Customizer.withDefaults()) // Enable OpenID Connect 1.0
            )
        .authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
        .exceptionHandling(
            (exceptions) ->
                exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/account/login/form"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain userInfoSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder)
      throws Exception {
    http.securityMatcher("/connect/userinfo")
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt((jwt) -> jwt.decoder(jwtDecoder)));

    return http.build();
  }

  @Bean
  @Order(3)
  public SecurityFilterChain defaultSecurityFilterChain(
      HttpSecurity http, SavedRequestAwareAuthenticationSuccessHandler successHandler)
      throws Exception {

    http.authorizeHttpRequests(
            authz ->
                authz
                    // observability endpoints: probed by Docker healthchecks and Prometheus
                    // from inside the compose network, no session there
                    .requestMatchers("/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    // static assets: the login/signup pages load the stylesheet pre-auth
                    .requestMatchers("/css/**")
                    .permitAll()
                    .requestMatchers("/account/signup/**")
                    .permitAll() // open registration
                    .requestMatchers("/account/login/**")
                    .permitAll() // login as well
                    .anyRequest()
                    .authenticated())
        .formLogin(
            login ->
                login
                    .loginPage("/account/login/form")
                    .loginProcessingUrl("/account/login") // form send to endpoint
                    .successHandler(successHandler)
                    .failureUrl("/account/login/form?error")
                    .permitAll())
        .logout(
            logout ->
                logout
                    .logoutUrl("/account/logout")
                    .logoutSuccessUrl("/account/login/form")
                    .permitAll());

    return http.build();
  }

  @Bean
  public SavedRequestAwareAuthenticationSuccessHandler
      savedRequestAwareAuthenticationSuccessHandler() {
    SavedRequestAwareAuthenticationSuccessHandler handler =
        new SavedRequestAwareAuthenticationSuccessHandler();
    handler.setDefaultTargetUrl("/account/current");
    return handler;
  }

  @Bean
  public UserDetailsService userDetailsService() {
    return username ->
        userRepository
            .findByUsername(username)
            .map(
                (User user) ->
                    org.springframework.security.core.userdetails.User.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .authorities(
                            user.getRoles().stream().map(Enum::name).toArray(String[]::new))
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    // delegating encoder: new passwords are stored as {bcrypt}...,
    // client secrets below keep the {noop} prefix
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  public RegisteredClientRepository registeredClientRepository(
      PasswordEncoder passwordEncoder,
      @Value("${CLIENT_OAUTH_SECRET:secret}") String clientSecret,
      @Value("${CART_SERVICE_SECRET:cart-service-secret}") String cartServiceSecret,
      @Value("${ORDER_SERVICE_SECRET:order-service-secret}") String orderServiceSecret,
      @Value("${PAYMENT_SERVICE_SECRET:payment-service-secret}") String paymentServiceSecret) {
    RegisteredClient client =
        RegisteredClient.withId("client")
            .clientId("client")
            .clientName("client")
            .clientSecret("{noop}" + clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:8080/login/oauth2/code/client")
            // must exactly match post_logout_redirect_uri sent by the client on RP-initiated logout
            .postLogoutRedirectUri("http://localhost:8080")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .scope(OidcScopes.PHONE)
            .scope("products.read")
            .clientSettings(
                ClientSettings.builder()
                    .requireAuthorizationConsent(false)
                    .requireProofKey(false)
                    .build())
            .tokenSettings(
                TokenSettings.builder()
                    // access must live noticeably longer than the client's 60s refresh
                    // clock skew, otherwise every request triggers a token refresh
                    .accessTokenTimeToLive(java.time.Duration.ofMinutes(5))
                    .refreshTokenTimeToLive(java.time.Duration.ofMinutes(30))
                    .reuseRefreshTokens(false)
                    .build())
            .build();

    RegisteredClient cartServiceClient =
        RegisteredClient.withId("cart-service")
            .clientId("cart-service")
            .clientSecret("{noop}" + cartServiceSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("products.read")
            .scope("products.write")
            .build();

    RegisteredClient orderServiceClient =
        RegisteredClient.withId("order-service")
            .clientId("order-service")
            .clientSecret("{noop}" + orderServiceSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("carts.read")
            .scope("carts.write")
            .build();

    RegisteredClient paymentServiceClient =
        RegisteredClient.withId("payment-service")
            .clientId("payment-service")
            .clientSecret("{noop}" + paymentServiceSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("orders.write")
            // subscriptions: payment snapshots the cart and clears it after subscribing
            .scope("carts.read")
            .scope("carts.write")
            .build();

    return new InMemoryRegisteredClientRepository(
        List.of(client, cartServiceClient, orderServiceClient, paymentServiceClient));
  }

  // reworked customizer, check operability of this later!!
  @Bean
  public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {

    return context -> {
      if (context.getPrincipal() == null) return;

      String username = context.getPrincipal().getName();
      // client_credentials: principal is a service client, not a user — no user claims to add
      User user = userRepository.findByUsername(username).orElse(null);
      if (user == null) return;

      Set<String> scopes = context.getAuthorizedScopes();

      // ID TOKEN
      if ("id_token".equals(context.getTokenType().getValue())) {

        // the BFF (client) maps this claim to session roles — see its userAuthoritiesMapper
        context
            .getClaims()
            .claim(
                "authorities",
                user.getRoles().stream().map(role -> "ROLE_" + role.name()).toList());

        if (scopes.contains("profile")) {
          context.getClaims().claim("fullName", user.getFullName());
        }
        if (scopes.contains("email")) {
          context.getClaims().claim("email", user.getEmail());
        }
        if (scopes.contains("phone")) {
          context.getClaims().claim("phone", user.getPhone());
        }
      }

      // ACCESS TOKEN
      if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        context
            .getClaims()
            .claim(
                "authorities",
                user.getRoles().stream().map(role -> "ROLE_" + role.name()).toList());
        // resource services (cart, order, ...) identify the user by this claim
        context.getClaims().claim("uid", user.getId());
      }
    };
  }

  /*@Bean
  public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {

      return context -> {
          if (context.getPrincipal() != null) {
              String username = context.getPrincipal().getName();
              User user = userRepository.findByUsername(username).orElseThrow();

              context.getClaims().claim("fullName", user.getFullName());
              context.getClaims().claim("email", user.getEmail());
              context.getClaims().claim("phone", user.getPhone());
              context.getClaims().claim("roles", user.getRoles());
          }
      };*/

  /*return context -> {
      var principal = context.getPrincipal();

      if (context.getTokenType().getValue().equals(OidcParameterNames.ID_TOKEN)) {
          var user = (org.springframework.security.core.userdetails.User) principal.getPrincipal();
          context.getClaims()
                  .claim("preferred_username", user.getUsername());
          // Overwrite the exp of the ID TOKEN so the tester can longer test
          context.getClaims().claim("exp", Instant.now().plus(3, MINUTES));
      }
  };*/
  // }

  @Bean
  public JWKSource<SecurityContext> jwkSource() throws Exception {
    RSAPublicKey publicKey = publicKey();
    RSAPrivateKey privateKey = privateKey();
    RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(JWK_KEY_ID).build();
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
  }

  @Bean
  public RSAPublicKey publicKey() throws Exception {
    byte[] keyBytes = Base64.getDecoder().decode(JWT_PUBLIC_KEY);
    var spec = new X509EncodedKeySpec(keyBytes);
    return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
  }

  @Bean
  public RSAPrivateKey privateKey() throws Exception {
    byte[] keyBytes = Base64.getDecoder().decode(JWT_PRIVATE_KEY);
    var spec = new PKCS8EncodedKeySpec(keyBytes);
    return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
  }

  @Bean
  public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
  }

  @Bean
  public AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder()
        .issuer("http://auth.local:9000")
        .authorizationEndpoint("/oauth2/authorize")
        .pushedAuthorizationRequestEndpoint("/oauth2/par")
        .deviceAuthorizationEndpoint("/oauth2/device_authorization")
        .deviceVerificationEndpoint("/oauth2/device_verification")
        .tokenEndpoint("/oauth2/token")
        .tokenIntrospectionEndpoint("/oauth2/introspect")
        .tokenRevocationEndpoint("/oauth2/revoke")
        .jwkSetEndpoint("/oauth2/jwks")
        .oidcLogoutEndpoint("/connect/logout")
        .oidcUserInfoEndpoint("/userinfo")
        .oidcClientRegistrationEndpoint("/connect/register")
        .build();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }
}
