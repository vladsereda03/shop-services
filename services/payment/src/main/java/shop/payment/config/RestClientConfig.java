package shop.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

  // the injected builder comes pre-configured by Boot (ObservationRegistry for metrics and
  // trace propagation); a static RestClient.builder() would silently drop all of that.
  // clone() keeps the shared builder bean unmodified (tests inject a singleton stub builder)
  @Bean
  public RestClient restClient(
      RestClient.Builder restClientBuilder, OAuth2AuthorizedClientManager authorizedClientManager) {
    OAuth2ClientHttpRequestInterceptor requestInterceptor =
        new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
    requestInterceptor.setClientRegistrationIdResolver(clientRegistrationIdResolver());

    return restClientBuilder.clone().requestInterceptor(requestInterceptor).build();
  }

  private static OAuth2ClientHttpRequestInterceptor.ClientRegistrationIdResolver
      clientRegistrationIdResolver() {
    return (request) -> "shop-api";
  }
}
