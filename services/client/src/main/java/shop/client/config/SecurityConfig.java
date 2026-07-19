package shop.client.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.AuthorizationCodeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.DelegatingOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizedClientRefreshedEventListener;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository)
      throws Exception {

    http.authorizeHttpRequests(
            auth ->
                auth
                    // observability endpoints: probed by Docker healthchecks and Prometheus
                    // from inside the compose network, no session there
                    .requestMatchers("/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    // static assets: no session needed to fetch the stylesheet
                    .requestMatchers("/css/**")
                    .permitAll()
                    .requestMatchers("/goods/add")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2Client(Customizer.withDefaults())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(
                        userInfo -> userInfo.userAuthoritiesMapper(userAuthoritiesMapper()))
                    .failureHandler(
                        (request, response, exception) -> {
                          exception.printStackTrace();
                          response.sendRedirect("/login?error");
                        }))
        .logout(
            logout ->
                logout.logoutSuccessHandler(
                    oidcLogoutSuccessHandler(clientRegistrationRepository)));

    return http.build();
  }

  // RP-initiated logout: after the local session is invalidated, the browser is sent
  // to auth's end_session_endpoint (/connect/logout, taken from OIDC discovery)
  // with id_token_hint, and auth redirects back to {baseUrl} after killing its session
  private LogoutSuccessHandler oidcLogoutSuccessHandler(
      ClientRegistrationRepository clientRegistrationRepository) {
    OidcClientInitiatedLogoutSuccessHandler handler =
        new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
    handler.setPostLogoutRedirectUri("{baseUrl}");
    return handler;
  }

  // maps the "authorities" claim of the ID token (ROLE_* values put there by auth)
  // into session authorities so hasRole/sec:authorize work in the BFF itself
  @Bean
  public GrantedAuthoritiesMapper userAuthoritiesMapper() {
    return authorities -> {
      Set<GrantedAuthority> mapped = new HashSet<>(authorities);
      authorities.forEach(
          authority -> {
            if (authority instanceof OidcUserAuthority oidcUserAuthority) {
              List<String> roles =
                  oidcUserAuthority.getIdToken().getClaimAsStringList("authorities");
              if (roles != null) {
                roles.stream().map(SimpleGrantedAuthority::new).forEach(mapped::add);
              }
            }
          });
      return mapped;
    };
  }

  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository clientRegistrationRepository,
      OAuth2AuthorizedClientRepository authorizedClientRepository,
      ApplicationEventPublisher applicationEventPublisher) {

    // assembled by hand instead of OAuth2AuthorizedClientProviderBuilder: the refresh
    // provider must publish OAuth2AuthorizedClientRefreshedEvent, otherwise the OidcUser
    // (and its ID token) in the session goes stale after the first refresh and
    // RP-initiated logout fails — auth only recognizes the latest ID token as id_token_hint
    RefreshTokenOAuth2AuthorizedClientProvider refreshTokenProvider =
        new RefreshTokenOAuth2AuthorizedClientProvider();
    refreshTokenProvider.setApplicationEventPublisher(applicationEventPublisher);

    OAuth2AuthorizedClientProvider authorizedClientProvider =
        new DelegatingOAuth2AuthorizedClientProvider(
            new AuthorizationCodeOAuth2AuthorizedClientProvider(), refreshTokenProvider);

    DefaultOAuth2AuthorizedClientManager authorizedClientManager =
        new DefaultOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientRepository);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }

  // step 2 of the refresh chain: validates the new ID token, reloads userinfo and publishes
  // OidcUserRefreshedEvent (handled by OidcUserSessionRefreshListener); our authorities
  // mapper is plugged in so the refreshed principal keeps its ROLE_* authorities
  @Bean
  public OidcAuthorizedClientRefreshedEventListener oidcAuthorizedClientRefreshedEventListener() {
    OidcAuthorizedClientRefreshedEventListener listener =
        new OidcAuthorizedClientRefreshedEventListener();
    listener.setAuthoritiesMapper(userAuthoritiesMapper());
    return listener;
  }
}
