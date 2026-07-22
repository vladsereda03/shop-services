package shop.auth.config;

import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

  // replicas and min.insync.replicas default to the 3-broker compose cluster; the single-broker
  // integration test overrides them to 1 so KafkaAdmin does not log an
  // InvalidReplicationFactorException while creating the topic at startup
  @Bean
  NewTopic userRegisteredTopic(
      @Value("${app.kafka.topic.user-registered.replicas:3}") int replicas,
      @Value("${app.kafka.topic.user-registered.min-insync-replicas:2}") String minInsyncReplicas) {
    return TopicBuilder.name("user-registered-events-topic")
        .partitions(3)
        .replicas(replicas)
        .configs(Map.of("min.insync.replicas", minInsyncReplicas))
        .build();
  }
}
