package shop.product.config;

import java.util.ArrayList;
import java.util.Collection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // stock operations: service-to-service (cart) via client_credentials scope
                    .requestMatchers(
                        HttpMethod.POST, "/api/products/*/reserve", "/api/products/*/release")
                    .hasAuthority("SCOPE_products.write")
                    // catalog management: a real user with the ADMIN role (authorities claim)
                    .requestMatchers(HttpMethod.POST, "/api/products")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/manufacturers")
                    .hasAuthority("SCOPE_products.read")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .build();
  }

  // default converter only maps the "scope" claim (SCOPE_...); we also map the
  // "authorities" claim our auth server puts into access tokens (ROLE_-prefixed already)
  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    JwtGrantedAuthoritiesConverter roleConverter = new JwtGrantedAuthoritiesConverter();
    roleConverter.setAuthoritiesClaimName("authorities");
    roleConverter.setAuthorityPrefix("");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Collection<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
          authorities.addAll(roleConverter.convert(jwt));
          return authorities;
        });
    return converter;
  }
}
