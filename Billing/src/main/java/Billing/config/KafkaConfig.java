package Billing.config;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import Billing.event.PlantRosterEvent;
import Billing.event.ZoneCapacityEvent;
import Billing.event.ZoneDemandEvent;

/**
 * Kafka wiring. Consumer side only -- Billing produces nothing, it only reads {@code
 * customer.demand} as a second, independent consumer group alongside Distributor's own, and now
 * also {@code distributor.zone-capacity} to mirror Distributor's zone-capacity config (see
 * {@code Billing.billing.ZoneCapacityCache}).
 *
 * <p>
 * Two inbound types now, so -- like Distributor and Grid -- this needs the named-factory-per-type
 * pattern rather than relying on the single default {@code kafkaListenerContainerFactory} bean
 * name Customer's and Producer's single-consumer-type configs can use.
 *
 * <p>
 * Note {@code JacksonJsonDeserializer} rather than {@code JsonDeserializer}: under Spring Boot 4 /
 * spring-kafka 4 the unprefixed class is the deprecated Jackson 2 binding, and this project is on
 * Jackson 3.
 */
@Configuration
public class KafkaConfig {

    /**
     * The same defensive shape as every other consumer in this project: type resolved by the
     * Java type argument rather than a Kafka type header (Customer stamps its own fully-qualified
     * class name, which does not exist here), wrapped so a malformed record cannot wedge the
     * partition forever.
     */
    @Bean
    ConsumerFactory<String, ZoneDemandEvent> zoneDemandConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        var delegate = new JacksonJsonDeserializer<>(ZoneDemandEvent.class, false);
        var valueDeserializer = new ErrorHandlingDeserializer<>(delegate);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ZoneDemandEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, ZoneDemandEvent> zoneDemandConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, ZoneDemandEvent>();
        factory.setConsumerFactory(zoneDemandConsumerFactory);
        return factory;
    }

    /**
     * A distinct consumer group and its own {@code earliest} offset reset -- unlike the billing
     * cycle above, which only ever needs to react to what happens from here on, {@link
     * Billing.billing.ZoneCapacityCache} needs the <em>full</em> current configuration rebuilt
     * after every restart, not just changes that happen to arrive afterward. Capacity changes are
     * low-volume (an admin edit, not a per-tick event), so replaying the whole topic on startup
     * costs nothing worth avoiding.
     *
     * <p>
     * {@code earliest} on its own is not enough for that, and was quietly not delivering it: it
     * only applies when Kafka has <em>no committed offset</em> for the group, and the container
     * commits one after every batch by default. The very first time this group ran that was
     * indistinguishable from a genuine full replay -- there was nothing committed yet -- but every
     * restart since has resumed from wherever it left off, exactly like a normal at-least-once
     * consumer, and left the mirror below missing anything published before that offset. Found by
     * the plant-roster mirror going empty on a restart, which shares this exact setup -- see
     * {@code plantRosterConsumerFactory} below. {@link
     * #zoneCapacityListenerContainerFactory(ConsumerFactory)} carries the actual fix.
     */
    @Bean
    ConsumerFactory<String, ZoneCapacityEvent> zoneCapacityConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "billing-service-zone-capacity");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var delegate = new JacksonJsonDeserializer<>(ZoneCapacityEvent.class, false);
        var valueDeserializer = new ErrorHandlingDeserializer<>(delegate);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ZoneCapacityEvent> zoneCapacityListenerContainerFactory(
            ConsumerFactory<String, ZoneCapacityEvent> zoneCapacityConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, ZoneCapacityEvent>();
        factory.setConsumerFactory(zoneCapacityConsumerFactory);
        // MANUAL, and ZoneCapacityListener never acknowledges: this group commits no offset, ever,
        // so `earliest` above finds none to resume from on every single restart and genuinely
        // replays the whole topic, rebuilding the cache from scratch as the class doc promises --
        // see this factory's own doc comment for what committing normally would have broken.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    /**
     * A third named factory, same reasoning as {@code zoneCapacityConsumerFactory}: {@link
     * Billing.billing.PlantRosterCache} needs the full current plant roster rebuilt after every
     * restart, not just changes from here on, so this gets its own consumer group and an
     * {@code earliest} offset reset. Roster changes are admin-driven and low-volume, so a full
     * replay on startup costs nothing worth avoiding.
     */
    @Bean
    ConsumerFactory<String, PlantRosterEvent> plantRosterConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "billing-service-plant-roster");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var delegate = new JacksonJsonDeserializer<>(PlantRosterEvent.class, false);
        var valueDeserializer = new ErrorHandlingDeserializer<>(delegate);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, PlantRosterEvent> plantRosterListenerContainerFactory(
            ConsumerFactory<String, PlantRosterEvent> plantRosterConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, PlantRosterEvent>();
        factory.setConsumerFactory(plantRosterConsumerFactory);
        // See zoneCapacityListenerContainerFactory's own comment: this group must never commit an
        // offset either, or `earliest` above only ever fires once and every later restart resumes
        // a stale mirror instead of rebuilding it -- which is exactly how this cache went empty
        // after a Billing restart even though PlantRosterListener was working correctly.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
