package Customer.infrastructure.kafka;

import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * Kafka wiring. Producer side only -- this service consumes nothing.
 *
 * <p>
 * Note {@code JacksonJsonSerializer} rather than {@code JsonSerializer}: under
 * Spring Boot 4 the
 * unprefixed class is the deprecated Jackson 2 binding, and the Jackson 3
 * databind this project
 * ships is what the prefixed one binds against.
 */
@Configuration
public class KafkaConfig {

    /** Matches {@code producer.output}, so both demand and generation fan out the same way. */
    private static final int DEMAND_TOPIC_PARTITIONS = 3;
    private static final short DEMAND_TOPIC_REPLICAS = 1;

    /**
     * Declared explicitly rather than left to broker auto-creation, which would give
     * a single
     * partition and make keying by zone pointless.
     */
    @Bean
    NewTopic customerDemandTopic() {
        return TopicBuilder.name(KafkaDemandPublisher.TOPIC)
                .partitions(DEMAND_TOPIC_PARTITIONS)
                .replicas(DEMAND_TOPIC_REPLICAS)
                .build();
    }

    @Bean
    ProducerFactory<String, ZoneDemandEvent> zoneDemandProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JacksonJsonSerializer<>());
    }

    @Bean
    KafkaTemplate<String, ZoneDemandEvent> zoneDemandKafkaTemplate(
            ProducerFactory<String, ZoneDemandEvent> zoneDemandProducerFactory) {
        return new KafkaTemplate<>(zoneDemandProducerFactory);
    }
}
