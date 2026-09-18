package Billing.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import Billing.billing.PlantRosterCache;
import Billing.event.PlantRosterEvent;

/**
 * Keeps {@link PlantRosterCache} in step with Producer's plant roster -- a config mirror, not a
 * billing action, so like {@link ZoneCapacityListener} there is nothing here that needs
 * idempotency: replaying the same roster change twice just overwrites the cache with the same
 * value.
 *
 * <p>
 * A failure here is logged and swallowed for the same reason every other listener in this project
 * does that -- see {@link ZoneDemandListener}.
 */
@Component
public class PlantRosterListener {

    private static final Logger log = LoggerFactory.getLogger(PlantRosterListener.class);

    private final PlantRosterCache cache;

    public PlantRosterListener(PlantRosterCache cache) {
        this.cache = cache;
    }

    @KafkaListener(topics = "producer.plants",
            containerFactory = "plantRosterListenerContainerFactory",
            autoStartup = "${billing.autostart:true}")
    void onPlantRosterChanged(PlantRosterEvent event) {
        try {
            if (event.removed()) {
                cache.remove(event.plantId());
            } else {
                cache.set(event.plantId(), event.type(), event.capacityMw(), event.active());
            }
        } catch (Exception e) {
            log.error("Failed to apply roster change for plant {}", event.plantId(), e);
        }
    }
}
