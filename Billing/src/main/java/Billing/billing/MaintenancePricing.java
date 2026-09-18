package Billing.billing;

import java.util.Map;

/**
 * Recurring upkeep owed per MW of active capacity, by plant type -- charged by {@link
 * MaintenanceChargeJob} on top of (not instead of) whatever a plant already earns or cost to
 * build. Hardcoded rather than {@code @ConfigurationProperties}-bound, the same choice {@link
 * PlantPricing} already makes for the same reason: these are calibrated game-economy numbers, not
 * deployment configuration a real operator would ever need to change per environment.
 *
 * <p>
 * Thermal plants cost the most to keep running (fuel handling, combustion maintenance), wind the
 * least (few moving parts relative to swept capacity) -- the same relative ordering {@link
 * PlantPricing}'s build cost already uses.
 */
public final class MaintenancePricing {

    private static final Map<PlantType, Double> RATE_PER_MW = Map.of(
            PlantType.THERMAL, 6.0,
            PlantType.WIND, 3.0,
            PlantType.SOLAR, 2.0);

    private MaintenancePricing() {
    }

    public static double costFor(PlantType type, double capacityMw) {
        return RATE_PER_MW.get(type) * capacityMw;
    }
}
