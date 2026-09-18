package Billing.billing;

import java.util.Map;

/**
 * kWh-sold thresholds that unlock each plant type for purchase -- see {@link UnlockService} for
 * how the running total is computed. THERMAL is unlocked from zero (every simulation starts able
 * to build one), SOLAR and WIND behind reachable thresholds so a fresh grid has something to work
 * toward before the full fleet is available, mirroring the reference game's tech-tree-gated
 * plant types (see the architecture comparison this feature came out of).
 */
public final class UnlockThresholds {

    private static final Map<PlantType, Double> THRESHOLDS = Map.of(
            PlantType.THERMAL, 0.0,
            PlantType.SOLAR, 50_000.0,
            PlantType.WIND, 150_000.0);

    private UnlockThresholds() {
    }

    public static double thresholdFor(PlantType type) {
        return THRESHOLDS.get(type);
    }

    public static boolean isUnlocked(PlantType type, double cumulativeKwhSold) {
        return cumulativeKwhSold >= thresholdFor(type);
    }
}
