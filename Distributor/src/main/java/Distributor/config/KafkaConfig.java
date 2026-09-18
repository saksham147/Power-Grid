package Distributor.config;

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

import Distributor.event.ProducerOutputEvent;
import Distributor.event.ZoneBalanceEvent;
import Distributor.event.ZoneCapacityEvent;
import Distributor.event.ZoneDemandEvent;
import Distributor.kafka.ZoneBalancePublisher;
import Distributor.kafka.ZoneCapacityEventPublisher;

/**
 * Kafka wiring: consumes {@code producer.output} and {@code customer.demand}, produces
 * {@code distributor.zone-balance}.
 *
 * <p>
 * Two inbound record types means two listener container factories, each named explicitly and
 * referenced from its {@code @KafkaListener} by name -- unlike Producer and Customer, which each
 * consume only one type and can rely on the default {@code kafkaListenerContainerFactory} name.
 *
 * <p>
 * Note {@code JacksonJson*} rather than {@code Json*}: under Spring Boot 4 / spring-kafka 4 the
 * unprefixed {@code JsonSerializer} and {@code JsonDeserializer} are the deprecated Jackson 2
 * classes, and this project is on Jackson 3.
 */
@Configuration
public class KafkaConfig {

    private static final int BALANCE_TOPIC_PARTITIONS = 3;
    private static final short BALANCE_TOPIC_REPLICAS = 1;

    /**
     * Declared explicitly rather than left to broker auto-creation, which would give a single
     * partition and make keying by zone id pointless.
     */
    @Bean
    NewTopic distributorZoneBalanceTopic() {
        return TopicBuilder.name(ZoneBalancePublisher.TOPIC)
                .partitions(BALANCE_TOPIC_PARTITIONS)
                .replicas(BALANCE_TOPIC_REPLICAS)
                .build();
    }

    /** Same partitioning as the balance topic -- see above; capacity changes are low-volume but
     *  still keyed by zone id, so one partition each is enough to keep a zone's own history ordered. */
    @Bean
    NewTopic distributorZoneCapacityTopic() {
        return TopicBuilder.name(ZoneCapacityEventPublisher.TOPIC)
                .partitions(BALANCE_TOPIC_PARTITIONS)
                .replicas(BALANCE_TOPIC_REPLICAS)
                .build();
    }

    /**
     * The same defensive shape as Producer's and Customer's: type resolved by the Java type
     * argument rather than a Kafka type header (Producer stamps its own fully-qualified class
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
    ConsumerFactory<String, ZoneDemandEvent> zoneDemandConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        var delegate = new JacksonJsonDeserializer<>(ZoneDemandEvent.class, false);
        var valueDeserializer = new ErrorHandlingDeserializer<>(delegate);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ZoneDemandEvent> zoneDemandListenerContainerFactory(
            ConsumerFactory<String, ZoneDemandEvent> zoneDemandConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, ZoneDemandEvent>();
        factory.setConsumerFactory(zoneDemandConsumerFactory);
        return factory;
    }

    @Bean
    ProducerFactory<String, ZoneBalanceEvent> zoneBalanceProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JacksonJsonSerializer<>());
    }

    @Bean
    KafkaTemplate<String, ZoneBalanceEvent> zoneBalanceKafkaTemplate(
            ProducerFactory<String, ZoneBalanceEvent> zoneBalanceProducerFactory) {
        return new KafkaTemplate<>(zoneBalanceProducerFactory);
    }

    @Bean
    ProducerFactory<String, ZoneCapacityEvent> zoneCapacityProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JacksonJsonSerializer<>());
    }

    @Bean
    KafkaTemplate<String, ZoneCapacityEvent> zoneCapacityKafkaTemplate(
            ProducerFactory<String, ZoneCapacityEvent> zoneCapacityProducerFactory) {
        return new KafkaTemplate<>(zoneCapacityProducerFactory);
    }
}
