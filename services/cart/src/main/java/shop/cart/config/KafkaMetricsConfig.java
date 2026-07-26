package shop.cart.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.MicrometerConsumerListener;

// Boot's autoconfigured consumer factory carries no client-metrics binding, so the Kafka
// consumer's native metrics (records-lag among them) never reach Micrometer. Attaching a
// MicrometerConsumerListener exports them to Prometheus, which is what the KafkaConsumerLagHigh
// alert keys off.
@Configuration
public class KafkaMetricsConfig {

  @Bean
  public DefaultKafkaConsumerFactoryCustomizer kafkaConsumerMicrometer(
      MeterRegistry meterRegistry) {
    return factory -> factory.addListener(new MicrometerConsumerListener<>(meterRegistry));
  }
}
