package Billing.billing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoragePricingTests {

    @Test
    void smallUnitsAreChargedTheFlatFloor() {
        assertThat(StoragePricing.cost(StorageKind.BATTERY, 1.0)).isEqualTo(3000.0);
        assertThat(StoragePricing.cost(StorageKind.HYDROGEN, 1.0)).isEqualTo(6000.0);
    }

    @Test
    void hydrogenCostsMorePerKwhThanABatteryAtTheSameCapacity() {
        double batteryCost = StoragePricing.cost(StorageKind.BATTERY, 1000.0);
        double hydrogenCost = StoragePricing.cost(StorageKind.HYDROGEN, 1000.0);

        assertThat(batteryCost).isEqualTo(1500 + 5 * 1000.0);
        assertThat(hydrogenCost).isEqualTo(3000 + 8 * 1000.0);
        assertThat(hydrogenCost).isGreaterThan(batteryCost);
    }
}
