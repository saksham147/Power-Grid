package Billing.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import Billing.billing.ZoneCapacityCache;
import Billing.event.ZoneCapacityEvent;

/**
 * Keeps {@link ZoneCapacityCache} in step with Distributor's zone-capacity configuration -- a
 * config mirror, not a billing action, so unlike {@link ZoneDemandListener} there is nothing here
 * that needs idempotency: replaying the same capacity change twice just overwrites the cache with
 * the same value.
 *
 * <p>
 * A failure here is logged and swallowed for the same reason every other listener in this project
 * does that -- see {@link ZoneDemandListener}.
 */
@Component
public class ZoneCapacityListener {

    private static final Logger log = LoggerFactory.getLogger(ZoneCapacityListener.class);

    private final ZoneCapacityCache cache;

    public ZoneCapacityListener(ZoneCapacityCache cache) {
        this.cache = cache;
    }

    @KafkaListener(topics = "distributor.zone-capacity",
            containerFactory = "zoneCapacityListenerContainerFactory",
            autoStartup = "${billing.autostart:true}")
    void onZoneCapacityChanged(ZoneCapacityEvent event) {
        try {
            cache.set(event.zoneId(), event.capacityKw());
        } catch (Exception e) {
            log.error("Failed to apply capacity change for zone {}", event.zoneId(), e);
        }
    }
}
