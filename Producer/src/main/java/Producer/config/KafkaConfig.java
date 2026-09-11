package Producer.config;

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

import Producer.event.GridTickEvent;
import Producer.event.ProducerOutputEvent;
import Producer.kafka.ProducerOutputPublisher;

/**
 * Kafka wiring.
 *
 * <p>
 * Note {@code JacksonJson*} rather than {@code Json*}: under Spring Boot 4 /
 * spring-kafka 4 the
 * unprefixed {@code JsonSerializer} and {@code JsonDeserializer} are the
 * deprecated Jackson 2
 * classes, and this project is on Jackson 3.
 */
@Configuration
public class KafkaConfig {

    private static final int OUTPUT_TOPIC_PARTITIONS = 3;
    private static final short OUTPUT_TOPIC_REPLICAS = 1;

    /**
     * Declared explicitly rather than left to broker auto-creation, which would
     * give a single
     * partition and make keying by plant id pointless. Three partitions means
     * per-plant ordering is
     * real and downstream consumers can actually parallelise.
     */
    @Bean
    NewTopic producerOutputTopic() {
        return TopicBuilder.name(ProducerOutputPublisher.TOPIC)
                .partitions(OUTPUT_TOPIC_PARTITIONS)
                .replicas(OUTPUT_TOPIC_REPLICAS)
                .build();
    }

    @Bean
    ConsumerFactory<String, GridTickEvent> gridTickConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();

        // The 'false' pins the target type and ignores Kafka type headers outright.
        //
        // Grid stamps its own fully-qualified class name (Grid.event.GridTickEvent) on
        // each message,
        // and that class does not exist here. The deserializer does fall back to the
        // target type
        // when it cannot resolve such a header, so this is belt-and-braces rather than
        // a fix for a
        // hard failure -- but it removes the fallback path entirely, which means no
        // dependence on
        // what Grid happens to stamp, no trusted-packages list to maintain, and no
        // per-message
        // attempt to load a class that will never resolve. Declaring the type in Java
        // rather than
        // as a spring.json.value.default.type string in YAML also keeps it
        // compile-checked.
        var delegate = new JacksonJsonDeserializer<>(GridTickEvent.class, false);

        // A malformed tick would otherwise be redelivered forever and wedge the
        // partition. Wrapped,
        // it surfaces to the container's error handler, which logs and moves past it.
        var valueDeserializer = new ErrorHandlingDeserializer<>(delegate);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    /**
     * Named exactly {@code kafkaListenerContainerFactory} so {@code @KafkaListener}
     * picks it up
     * without every listener having to name it, and so Boot's auto-configured one
     * backs off.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, GridTickEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, GridTickEvent> gridTickConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, GridTickEvent>();
        factory.setConsumerFactory(gridTickConsumerFactory);
        return factory;
    }

    @Bean
    ProducerFactory<String, ProducerOutputEvent> producerOutputProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JacksonJsonSerializer<>());
    }

    @Bean
    KafkaTemplate<String, ProducerOutputEvent> producerOutputKafkaTemplate(
            ProducerFactory<String, ProducerOutputEvent> producerOutputProducerFactory) {
        return new KafkaTemplate<>(producerOutputProducerFactory);
    }
}
