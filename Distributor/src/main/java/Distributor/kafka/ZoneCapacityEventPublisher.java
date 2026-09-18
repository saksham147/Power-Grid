package Distributor.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import Distributor.distribution.ZoneCapacityPublisher;
import Distributor.event.ZoneCapacityEvent;

/**
 * The {@link ZoneCapacityPublisher} adapter: publishes a zone's capacity configuration to
 * {@code distributor.zone-capacity}, the same one-topic-per-outbound-type shape
 * {@link ZoneBalancePublisher} already uses. Capacities change rarely (an admin edit, not a
 * per-tick event), so unlike the balance topic there is no publish-volume concern here either.
 */
@Component
public class ZoneCapacityEventPublisher implements ZoneCapacityPublisher {

    public static final String TOPIC = "distributor.zone-capacity";

    private static final Logger log = LoggerFactory.getLogger(ZoneCapacityEventPublisher.class);

    private final KafkaTemplate<String, ZoneCapacityEvent> kafkaTemplate;

    public ZoneCapacityEventPublisher(KafkaTemplate<String, ZoneCapacityEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(ZoneCapacityEvent event) {
        // Keyed by zone id, matching every other zone-keyed topic here: one zone's capacity
        // history stays ordered on one partition.
        kafkaTemplate.send(TOPIC, event.zoneId(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish capacity for zone {}", event.zoneId(), error);
                    }
                });
    }
}
