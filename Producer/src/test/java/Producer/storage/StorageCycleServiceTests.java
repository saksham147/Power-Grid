package Producer.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import Producer.model.StorageKind;
import Producer.model.StorageUnit;

/** The charge/discharge physics: direction follows frequency deviation, magnitude is capped by
 *  both the rate limit and however much energy is actually available/has room. */
class StorageCycleServiceTests {

    private static final double HOURS_PER_TICK = 5.0 / 60.0;

    @Test
    void aDeficitDischargesAtTheRateLimit() {
        // 100 kWh battery, half charged (50 kWh), 600 kW max discharge -> 600*(5/60)=50 kWh
        // room to discharge this tick, exactly what's available.
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);

        double netKw = StorageCycleService.applyTick(unit, -0.05);

        assertThat(netKw).isCloseTo(600.0, within(1e-9));
        assertThat(unit.getStateOfChargeKwh()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void dischargeCannotExceedWhatIsActuallyStored() {
        // Only 10 kWh stored; the 600 kW rate would ask for 50 kWh, more than exists.
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);
        unit.setStateOfChargeKwh(10.0);

        double netKw = StorageCycleService.applyTick(unit, -0.05);

        assertThat(netKw).isCloseTo(10.0 / HOURS_PER_TICK, within(1e-9));
        assertThat(unit.getStateOfChargeKwh()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void aSurplusChargesAndReportsNegativeNetKw() {
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);
        unit.setStateOfChargeKwh(0.0);

        double netKw = StorageCycleService.applyTick(unit, 0.05);

        assertThat(netKw).isCloseTo(-600.0, within(1e-9));
        assertThat(unit.getStateOfChargeKwh()).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void chargeCannotExceedAvailableRoom() {
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);
        unit.setStateOfChargeKwh(95.0);

        double netKw = StorageCycleService.applyTick(unit, 0.05);

        assertThat(netKw).isCloseTo(-(5.0 / HOURS_PER_TICK), within(1e-9));
        assertThat(unit.getStateOfChargeKwh()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    void withinTheDeadbandTheUnitStaysIdle() {
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);
        double before = unit.getStateOfChargeKwh();

        double netKw = StorageCycleService.applyTick(unit, 0.01);

        assertThat(netKw).isZero();
        assertThat(unit.getStateOfChargeKwh()).isEqualTo(before);
    }
}
