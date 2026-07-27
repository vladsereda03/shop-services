package shop.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

// WebClient for the reactive SSE relay (Р7). Built from the injected, Boot-configured builder for
// the same reason RestClientConfig clones the RestClient builder: it carries the observation
// instrumentation (metrics + traceparent propagation) that a static WebClient.create() would drop.
@Configuration
public class WebClientConfig {

  @Bean
  public WebClient webClient(WebClient.Builder builder) {
    return builder.build();
  }
}
