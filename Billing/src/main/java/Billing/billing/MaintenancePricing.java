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
 *
 * <p>
 * Raised 5x from the original 6.0/3.0/2.0: at those rates upkeep was a rounding error next to
 * {@code billing.rate-per-kwh}'s revenue, so running a fleet cost nothing in practice and
 * decommissioning an idle plant was never worth doing. It stays a fraction of the price of just
 * running the same capacity at {@code billing.rate-per-kwh} 1.0.
 */
public final class MaintenancePricing {

    private static final Map<PlantType, Double> RATE_PER_MW = Map.of(
            PlantType.THERMAL, 30.0,
            PlantType.WIND, 15.0,
            PlantType.SOLAR, 10.0);

    private MaintenancePricing() {
    }

    public static double costFor(PlantType type, double capacityMw) {
        return RATE_PER_MW.get(type) * capacityMw;
    }
}
