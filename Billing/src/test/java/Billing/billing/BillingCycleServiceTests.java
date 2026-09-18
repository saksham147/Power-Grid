package Billing.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import Billing.event.ZoneDemandEvent;

/**
 * The use case, driven against a fake ledger -- no Spring context, no broker, no database:
 * {@code BillingCycleService} depends only on {@link BillingLedger} and the plain-object
 * {@link ZoneCapacityCache}, so a small stateful fake is enough to exercise the pricing math, the
 * overage surcharge, and the idempotency contract.
 */
class BillingCycleServiceTests {

    private static final double RATE_PER_KWH = 8.0;
    private static final double OVERAGE_MULTIPLIER = 1.5;

    /** Tracks (zoneId, tick) pairs already "billed", mirroring the real unique-constraint guard. */
    private static class FakeLedger implements BillingLedger {
        private final Set<String> billed = new HashSet<>();
        double lastBalance = 0.0;
        int applyCount = 0;

        @Override
        public Optional<BillingResult> apply(String zoneId, String zoneName, long tick, double kwh,
                double overageKwh, double ratePerKwh, double costRupees, double overageCostRupees,
                Instant timestamp) {
            if (!billed.add(zoneId + ":" + tick)) {
                return Optional.empty();
            }
            applyCount++;
            lastBalance -= costRupees;
            return Optional.of(new BillingResult(zoneId, zoneName, tick, kwh, overageKwh, ratePerKwh, costRupees,
                    overageCostRupees, lastBalance, timestamp));
        }
    }

    private static ZoneDemandEvent demand(String zoneId, long tick, double demandKw) {
        return new ZoneDemandEvent(zoneId, zoneId + "-name", tick, "00:05", 1000, demandKw, Instant.now());
    }

    private static BillingCycleService service(BillingLedger ledger) {
        return new BillingCycleService(RATE_PER_KWH, OVERAGE_MULTIPLIER, new ZoneCapacityCache(), ledger);
    }

    @Test
    void convertsFiveMinutesOfDemandIntoKwhAndAppliesTheRate() {
        var service = service(new FakeLedger());

        // 120 kW held for 5 simulated minutes = 120 * (5/60) = 10 kWh; at 8.0/kWh, cost = 80.0.
        BillingResult result = service.onZoneDemand(demand("Z-N", 1, 120.0)).orElseThrow();

        assertThat(result.kwh()).isCloseTo(10.0, within(1e-9));
        assertThat(result.costRupees()).isCloseTo(80.0, within(1e-9));
        assertThat(result.ratePerKwh()).isEqualTo(8.0);
        assertThat(result.overageKwh()).isZero();
        assertThat(result.overageCostRupees()).isZero();
    }

    @Test
    void zeroDemandBillsZeroCostRatherThanSkipping() {
        var service = service(new FakeLedger());

        BillingResult result = service.onZoneDemand(demand("Z-N", 1, 0.0)).orElseThrow();

        assertThat(result.kwh()).isZero();
        assertThat(result.costRupees()).isZero();
    }

    @Test
    void aSecondDeliveryOfTheSameTickIsSkippedRatherThanDoubleCharged() {
        var ledger = new FakeLedger();
        var service = service(ledger);

        Optional<BillingResult> first = service.onZoneDemand(demand("Z-N", 1, 120.0));
        Optional<BillingResult> redelivered = service.onZoneDemand(demand("Z-N", 1, 120.0));

        assertThat(first).isPresent();
        assertThat(redelivered).isEmpty();
        assertThat(ledger.applyCount).isEqualTo(1);
    }

    @Test
    void differentTicksForTheSameZoneAreBilledIndependently() {
        var service = service(new FakeLedger());

        assertThat(service.onZoneDemand(demand("Z-N", 1, 120.0))).isPresent();
        assertThat(service.onZoneDemand(demand("Z-N", 2, 120.0))).isPresent();
    }

    @Test
    void carriesZoneIdentityAndTimestampThrough() {
        var service = service(new FakeLedger());
        Instant at = Instant.parse("2026-01-01T00:00:00Z");

        BillingResult result = service.onZoneDemand(new ZoneDemandEvent("Z-N", "North", 42, "03:30", 500, 60, at))
                .orElseThrow();

        assertThat(result.zoneId()).isEqualTo("Z-N");
        assertThat(result.zoneName()).isEqualTo("North");
        assertThat(result.tick()).isEqualTo(42);
        assertThat(result.timestamp()).isEqualTo(at);
    }

    @Test
    void demandWithinCapacityIsBilledEntirelyAtTheNormalRate() {
        var capacities = new ZoneCapacityCache();
        capacities.set("Z-N", 200.0);
        var service = new BillingCycleService(RATE_PER_KWH, OVERAGE_MULTIPLIER, capacities, new FakeLedger());

        // 120 kW <= 200 kW capacity: no overage at all.
        BillingResult result = service.onZoneDemand(demand("Z-N", 1, 120.0)).orElseThrow();

        assertThat(result.overageKwh()).isZero();
        assertThat(result.overageCostRupees()).isZero();
        assertThat(result.costRupees()).isCloseTo(80.0, within(1e-9));
    }

    @Test
    void demandAboveCapacityIsSurchargedOnlyForTheOverage() {
        var capacities = new ZoneCapacityCache();
        capacities.set("Z-N", 60.0);
        var service = new BillingCycleService(RATE_PER_KWH, OVERAGE_MULTIPLIER, capacities, new FakeLedger());

        // 120 kW demand, 60 kW capacity: 60 kW within (5.0 kWh @ 8.0 = 40.0), 60 kW over
        // (5.0 kWh @ 8.0*1.5=12.0 = 60.0). Total 100.0.
        BillingResult result = service.onZoneDemand(demand("Z-N", 1, 120.0)).orElseThrow();

        assertThat(result.overageKwh()).isCloseTo(5.0, within(1e-9));
        assertThat(result.overageCostRupees()).isCloseTo(60.0, within(1e-9));
        assertThat(result.costRupees()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    void demandExactlyAtCapacityIsNotTreatedAsOverage() {
        var capacities = new ZoneCapacityCache();
        capacities.set("Z-N", 120.0);
        var service = new BillingCycleService(RATE_PER_KWH, OVERAGE_MULTIPLIER, capacities, new FakeLedger());

        BillingResult result = service.onZoneDemand(demand("Z-N", 1, 120.0)).orElseThrow();

        assertThat(result.overageKwh()).isZero();
        assertThat(result.costRupees()).isCloseTo(80.0, within(1e-9));
    }
}
