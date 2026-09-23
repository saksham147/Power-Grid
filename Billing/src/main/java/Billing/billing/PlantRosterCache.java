package Billing.billing;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Billing's read-only mirror of Producer's plant roster -- kept in memory, not a database table,
 * for the same reasons {@link ZoneCapacityCache} mirrors Distributor's zone capacities. Rebuilt in
 * full on every restart because the dedicated consumer group behind this cache reads {@code
 * producer.plants} from the earliest offset -- see {@code Billing.config.KafkaConfig
 * #plantRosterListenerContainerFactory}.
 *
 * <p>
 * Used by {@link MaintenanceChargeJob} to know which plants exist and what upkeep they owe,
 * without Billing ever calling Producer's REST API directly (cross-service REST calls aren't used
 * anywhere in this project -- Kafka mirroring is the established pattern).
 */
@Component
public class PlantRosterCache {

    /** @param type stored for {@link MaintenancePricing}'s per-type rate lookup */
    public record PlantSnapshot(Long plantId, PlantType type, double capacityMw, boolean active) {
    }

    private final ConcurrentHashMap<Long, PlantSnapshot> plantsById = new ConcurrentHashMap<>();

    public void set(Long plantId, PlantType type, double capacityMw, boolean active) {
        plantsById.put(plantId, new PlantSnapshot(plantId, type, capacityMw, active));
    }

    public void remove(Long plantId) {
        plantsById.remove(plantId);
    }

    /** Every known plant, active or not -- for a view that has to list an inactive plant too
     *  (at zero running cost) rather than have it silently vanish. */
    public Collection<PlantSnapshot> allPlants() {
        return List.copyOf(plantsById.values());
    }

    /** Only these owe maintenance -- an inactive plant draws no upkeep, matching how it also
     *  produces no output and is excluded from Producer's own per-tick generation pass. */
    public Collection<PlantSnapshot> activePlants() {
        return plantsById.values().stream().filter(PlantSnapshot::active).toList();
    }
}
