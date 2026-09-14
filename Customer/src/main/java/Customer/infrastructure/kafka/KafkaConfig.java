package Customer.infrastructure.kafka;

import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * Kafka wiring: produces {@code customer.demand}, and -- since Grid became this system's single
 * clock -- consumes {@code grid.tick}.
 *
 * <p>
 * Note {@code JacksonJson*} rather than {@code Json*}: under Spring Boot 4 / spring-kafka 4 the
 * unprefixed {@code JsonSerializer} and {@code JsonDeserializer} are the deprecated Jackson 2
 * classes, and this project is on Jackson 3.
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

    /**
     * The same defensive shape as Producer's: type resolved by the Java type argument rather than a
     * Kafka type header (Grid stamps {@code Grid.event.GridTickEvent}, a class that does not exist
     * here either), wrapped so a malformed tick cannot wedge the partition forever.
     */
    @Bean
    ConsumerFactory<String, GridTickEvent> gridTickConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        var delegate = new JacksonJsonDeserializer<>(GridTickEvent.class, false);
        var valueDeserializer = new ErrorHandlingDeserializer<>(delegate);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    /**
     * Named exactly {@code kafkaListenerContainerFactory} so {@code @KafkaListener} picks it up
     * without naming it, and so Boot's auto-configured one backs off.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, GridTickEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, GridTickEvent> gridTickConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, GridTickEvent>();
        factory.setConsumerFactory(gridTickConsumerFactory);
        return factory;
    }
}
