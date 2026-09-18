package Grid.config;

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

import Grid.event.GridTickEvent;
import Grid.event.ProducerOutputEvent;
import Grid.event.StorageOutputEvent;
import Grid.event.ZoneBalanceEvent;
import Grid.kafka.GridTickPublisher;

/**
 * Kafka wiring: produces {@code grid.tick}, and -- now that automatic frequency control needs
 * something to react to -- consumes {@code producer.output} and {@code distributor.zone-balance}.
 *
 * <p>
 * Two inbound record types means two listener container factories, each named explicitly and
 * referenced from its {@code @KafkaListener} by name, the same way Distributor's own
 * {@code KafkaConfig} does for its two inbound topics.
 *
 * <p>
 * Note {@code JacksonJson*} rather than {@code Json*}: under Spring Boot 4 / spring-kafka 4 the
 * unprefixed {@code JsonSerializer} and {@code JsonDeserializer} are the deprecated Jackson 2
 * classes, and this project is on Jackson 3.
 */
@Configuration
public class KafkaConfig {

    /**
     * Exactly one partition, unlike {@code producer.output} and {@code customer.demand}'s three.
     * Those key by an entity id because nothing needs to compare one plant's or zone's history
     * against another's ordering. This topic has one producer and every consumer must see every
     * tick in the same order it was issued -- ticks 41 and 42 processed out of order would rewind
     * the simulated world for whichever consumer saw them that way. A single partition is what
     * makes that guarantee free instead of something every consumer has to defend against.
     */
    private static final int TICK_TOPIC_PARTITIONS = 1;
    private static final short TICK_TOPIC_REPLICAS = 1;

    @Bean
    NewTopic gridTickTopic() {
        return TopicBuilder.name(GridTickPublisher.TOPIC)
                .partitions(TICK_TOPIC_PARTITIONS)
                .replicas(TICK_TOPIC_REPLICAS)
                .build();
    }

    @Bean
    ProducerFactory<String, GridTickEvent> gridTickProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JacksonJsonSerializer<>());
    }

    @Bean
    KafkaTemplate<String, GridTickEvent> gridTickKafkaTemplate(
            ProducerFactory<String, GridTickEvent> gridTickProducerFactory) {
        return new KafkaTemplate<>(gridTickProducerFactory);
    }

    /**
     * The same defensive shape as every other consumer in this project: type resolved by the Java
     * type argument rather than a Kafka type header (Producer stamps its own fully-qualified class
     * name, which does not exist here), wrapped so a malformed record cannot wedge the partition
     * forever.
     */
    @Bean
    ConsumerFactory<String, ProducerOutputEvent> producerOutputConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        var delegate = new JacksonJsonDeserializer<>(ProducerOutputEvent.class, false);
        var valueDeserializer = new ErrorHandlingDeserializer<>(delegate);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ProducerOutputEvent> producerOutputListenerContainerFactory(
            ConsumerFactory<String, ProducerOutputEvent> producerOutputConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, ProducerOutputEvent>();
        factory.setConsumerFactory(producerOutputConsumerFactory);
        return factory;
    }

    @Bean
    ConsumerFactory<String, ZoneBalanceEvent> zoneBalanceConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        var delegate = new JacksonJsonDeserializer<>(ZoneBalanceEvent.class, false);
        var valueDeserializer = new ErrorHandlingDeserializer<>(delegate);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ZoneBalanceEvent> zoneBalanceListenerContainerFactory(
            ConsumerFactory<String, ZoneBalanceEvent> zoneBalanceConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, ZoneBalanceEvent>();
        factory.setConsumerFactory(zoneBalanceConsumerFactory);
        return factory;
    }

    @Bean
    ConsumerFactory<String, StorageOutputEvent> storageOutputConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        var delegate = new JacksonJsonDeserializer<>(StorageOutputEvent.class, false);
        var valueDeserializer = new ErrorHandlingDeserializer<>(delegate);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, StorageOutputEvent> storageOutputListenerContainerFactory(
            ConsumerFactory<String, StorageOutputEvent> storageOutputConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, StorageOutputEvent>();
        factory.setConsumerFactory(storageOutputConsumerFactory);
        return factory;
    }
}
