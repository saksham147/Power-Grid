package Billing.billing;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import Billing.event.ZoneDemandEvent;

/**
 * The use case: turn one zone's tick-end demand reading into a billing charge.
 *
 * <p>
 * Deliberately free of Kafka and JPA specifics -- depends only on {@link BillingLedger}, which is
 * what keeps the pricing math testable with a fake and no broker or database, the same shape as
 * {@code Distributor.distribution.DistributionService}. {@link ZoneCapacityCache} is an exception
 * to "no framework specifics", but it is itself framework-free (a plain in-memory map), so it costs
 * this class nothing to depend on directly -- there is no adapter seam worth a third port for a
 * read this cheap.
 *
 * <h2>Why every tick is a billing cycle</h2>
 *
 * The spec calls for billing "every 5 simulated minutes". {@code SimulationClock} (canonical copy
 * in {@code Grid.simulation.SimulationClock}) fixes one tick at exactly 5 simulated minutes, so
 * there is no separate billing-period clock to track: this reacts to every {@code customer.demand}
 * event directly, exactly once per zone per tick, with no scheduler of its own -- the same
 * reasoning {@code DistributionService} already documents for why it has no {@code grid.tick}
 * listener either.
 *
 * <h2>The overage surcharge</h2>
 *
 * A zone can draw more than its assigned capacity (see {@code Distributor.model.ZoneCapacity}) --
 * that demand is never refused here, Distributor never caps delivery either (see {@code
 * DistributionService}'s own docs) -- but the portion above capacity is billed at {@code
 * ratePerKwh * overageRateMultiplier} rather than the normal rate. A zone with no capacity assigned
 * (nothing in {@link ZoneCapacityCache}) is uncapped: entirely at the normal rate, exactly as
 * before this feature existed.
 */
@Service
public class BillingCycleService {

    /** Minutes in an hour, for the kW -> kWh conversion below. */
    private static final double MINUTES_PER_HOUR = 60.0;

    /**
     * One tick's simulated span, in minutes. Not read from {@code SimulationClock} directly --
     * there is no shared library module between services, so, like every other cross-service
     * constant in this project, it is a local copy that must be kept numerically identical to the
     * canonical one in Grid.
     */
    private static final double SIMULATED_MINUTES_PER_TICK = 5.0;

    private final double ratePerKwh;
    private final double overageRateMultiplier;
    private final ZoneCapacityCache capacities;
    private final BillingLedger ledger;

    public BillingCycleService(@Value("${billing.rate-per-kwh}") double ratePerKwh,
            @Value("${billing.overage-rate-multiplier}") double overageRateMultiplier,
            ZoneCapacityCache capacities, BillingLedger ledger) {
        this.ratePerKwh = ratePerKwh;
        this.overageRateMultiplier = overageRateMultiplier;
        this.capacities = capacities;
        this.ledger = ledger;
    }

    /**
     * @return the applied charge, or empty if this tick was already billed for this zone (a
     *         duplicate delivery of the same event)
     */
    public Optional<BillingResult> onZoneDemand(ZoneDemandEvent event) {
        double kwh = event.demandKw() * (SIMULATED_MINUTES_PER_TICK / MINUTES_PER_HOUR);

        double overageKwh = capacities.get(event.zoneId())
                .filter(capacityKw -> event.demandKw() > capacityKw)
                .map(capacityKw -> kwh - capacityKw * (SIMULATED_MINUTES_PER_TICK / MINUTES_PER_HOUR))
                .orElse(0.0);
        double withinKwh = kwh - overageKwh;

        double overageCost = overageKwh * ratePerKwh * overageRateMultiplier;
        double cost = withinKwh * ratePerKwh + overageCost;

        return ledger.apply(event.zoneId(), event.zoneName(), event.tick(), kwh, overageKwh, ratePerKwh, cost,
                overageCost, event.timestamp());
    }
}
