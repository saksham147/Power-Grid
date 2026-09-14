package Distributor.distribution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import Distributor.event.ProducerOutputEvent;
import Distributor.event.ZoneDemandEvent;

/**
 * The use case: fold incoming supply into {@link GridStateTracker}, and on every incoming demand
 * event, merge it against the tracker's current state into one zone's balance -- log it, then
 * publish it.
 *
 * <p>
 * Deliberately free of Kafka and JPA annotations, the same way {@code Customer.application.
 * DemandSimulator} is: it depends on {@link DistributionLogger} and {@link BalancePublisher} only,
 * which is what keeps the allocation math testable with two fakes and no broker or database.
 *
 * <h2>Why demand, not a tick, drives the merge</h2>
 *
 * There is no {@code grid.tick} listener here. Producer and Customer both react to that tick
 * independently and in parallel, so a third listener here would have no ordering guarantee against
 * either of them and would just as often run before this tick's own output/demand had arrived as
 * after. Reacting directly to each {@code customer.demand} record instead means the demand side of
 * every balance is exactly current; only the supply side can lag by up to a tick, which
 * {@link GridStateTracker} already documents.
 *
 * <h2>The allocation</h2>
 *
 * Plants carry no zone of their own -- see {@code Producer.model.PowerPlant} -- so fleet-wide
 * supply is split across zones in proportion to each zone's share of total demand. A zone that
 * asks for twice as much is allocated twice as much of whatever the fleet is producing. That makes
 * {@code balanceKw} track the grid's overall surplus or shortfall, scaled by zone size, rather than
 * some per-zone transmission model this project has no data to support.
 *
 * <p>
 * That share is computed against {@link GridStateTracker#totalDemandKw()} as it stands the instant
 * this event arrives, not a completed tick's worth: the first zone to report in a round is
 * momentarily allocated the whole of whatever total is known so far, and the split only settles to
 * the true ratio once every zone has reported at least once. With ticks arriving every few seconds
 * this converges immediately and is not visible in practice.
 */
@Service
public class DistributionService {

    private static final Logger log = LoggerFactory.getLogger(DistributionService.class);

    private final GridStateTracker state;
    private final DistributionLogger logger;
    private final BalancePublisher publisher;

    public DistributionService(GridStateTracker state, DistributionLogger logger, BalancePublisher publisher) {
        this.state = state;
        this.logger = logger;
        this.publisher = publisher;
    }

    /** Folds one plant's latest output into the tracked state. Nothing to merge or publish yet. */
    public void onProducerOutput(ProducerOutputEvent event) {
        state.recordSupply(event.producerId(), event.outputMw());
    }

    /**
     * Merges one zone's demand against the fleet's current total supply, logs the result, and
     * publishes it.
     *
     * <p>
     * The log write and the publish are independent output ports, exactly like
     * {@code Customer.application.DemandSimulator.tick}: one failing must not cost the other, since
     * each already happened as far as its own downstream is concerned.
     *
     * @return the merged balance, for callers that want it (tests, mainly)
     */
    public ZoneDistribution onZoneDemand(ZoneDemandEvent event) {
        state.recordDemand(event.zoneId(), event.demandKw());

        double totalDemandKw = state.totalDemandKw();
        double totalSupplyKw = state.totalSupplyKw();
        double share = totalDemandKw > 0 ? event.demandKw() / totalDemandKw : 0.0;
        double suppliedKw = share * totalSupplyKw;
        double balanceKw = suppliedKw - event.demandKw();

        ZoneDistribution distribution = new ZoneDistribution(
                event.zoneId(), event.zoneName(), event.tick(), event.demandKw(), suppliedKw, balanceKw,
                event.timestamp());

        guard("record log", () -> logger.log(distribution));
        guard("publish", () -> publisher.publish(distribution));

        return distribution;
    }

    /**
     * Runs one output port, absorbing anything it throws -- see {@code DemandSimulator.guard} for
     * why: neither port is allowed to cost the other, or the event, its own outcome.
     */
    private static void guard(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Distribution {} failed; the pipeline continues", what, e);
        }
    }
}
