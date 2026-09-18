package Billing.billing;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Billing's read-only mirror of Distributor's zone-capacity configuration -- kept in memory, not a
 * database table, the same reasoning {@code Distributor.distribution.GridStateTracker} already
 * gives for its own in-memory maps: this is Distributor's data, rebuilt from the
 * {@code distributor.zone-capacity} broadcast rather than queried back or duplicated into a table
 * Billing would then have to keep in sync itself. A restart rebuilds it in full because the
 * dedicated consumer group behind this cache reads that topic from the earliest offset -- see
 * {@code Billing.config.KafkaConfig#zoneCapacityListenerContainerFactory}.
 *
 * <p>
 * A zone absent from this cache is uncapped: billed at the normal rate only, exactly as before this
 * feature existed. A capacity <em>removal</em> ends up looking exactly the same, since {@code set}
 * evicts the entry rather than storing a null.
 */
@Component
public class ZoneCapacityCache {

    private final ConcurrentHashMap<String, Double> capacityKwByZone = new ConcurrentHashMap<>();

    public void set(String zoneId, Double capacityKw) {
        if (capacityKw == null) {
            capacityKwByZone.remove(zoneId);
        } else {
            capacityKwByZone.put(zoneId, capacityKw);
        }
    }

    public Optional<Double> get(String zoneId) {
        return Optional.ofNullable(capacityKwByZone.get(zoneId));
    }
}
