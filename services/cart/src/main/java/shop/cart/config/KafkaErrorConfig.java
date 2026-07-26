package shop.cart.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

// Dead-letter handling for the user-registered consumer. Paired with the ErrorHandlingDeserializer
// in application.yaml: a message that cannot be deserialized (a poison pill) would otherwise be
// retried forever inside the poll and wedge the partition. Here it is routed to <topic>.DLT so the
// partition keeps moving, and an operator can inspect what failed.
@Configuration
public class KafkaErrorConfig {

  // matches the suffix DeadLetterPublishingRecoverer appends by default (`<topic>-dlt`), so the
  // default destination resolver and this declared topic line up
  public static final String USER_REGISTERED_DLT = "user-registered-events-topic-dlt";

  // same partition count as the source topic so the recoverer, which keeps the original partition
  // number, never resolves to a partition the DLT does not have; replicas default to the 3-broker
  // compose cluster and are overridden to 1 by the single-broker integration test
  @Bean
  public NewTopic userRegisteredDlt(@Value("${app.kafka.topic.dlt.replicas:3}") int replicas) {
    return TopicBuilder.name(USER_REGISTERED_DLT).partitions(3).replicas(replicas).build();
  }

  // Deserialization failures are not retryable (they will always fail), so DefaultErrorHandler
  // sends them straight to the recoverer; other listener errors get two quick retries first, then
  // dead-letter — never an unbounded loop.
  @Bean
  public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
  }
}
