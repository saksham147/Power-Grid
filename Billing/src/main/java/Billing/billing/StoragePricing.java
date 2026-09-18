package Billing.billing;

import java.util.Map;

/**
 * What a new storage unit costs to build, by kind and energy capacity -- kept separate from
 * {@link PlantPricing}/{@link PlantType} entirely, not extended to cover a storage "type", so that
 * adding storage never risks a {@code PlantPricing.RATES.get()} lookup miss on a type it was never
 * designed for.
 *
 * <p>
 * {@code cost = max(minimum, base + perKwh * capacityKwh)}: hydrogen costs more per kWh than a
 * battery here, reflecting the extra conversion equipment (electrolyser, fuel cell) a real
 * hydrogen storage system needs that a battery does not.
 */
public final class StoragePricing {

    private record Rate(double base, double perKwh, double minimum) {
    }

    private static final Map<StorageKind, Rate> RATES = Map.of(
            StorageKind.BATTERY, new Rate(1500, 5, 3000),
            StorageKind.HYDROGEN, new Rate(3000, 8, 6000));

    private StoragePricing() {
    }

    public static double cost(StorageKind kind, double capacityKwh) {
        Rate rate = RATES.get(kind);
        return Math.max(rate.minimum(), rate.base() + rate.perKwh() * capacityKwh);
    }
}
