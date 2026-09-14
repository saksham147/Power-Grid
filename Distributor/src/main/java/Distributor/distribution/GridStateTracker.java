package Distributor.distribution;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Distributor's current view of the grid: the latest known output of every plant, and the latest
 * known demand of every zone, kept in memory rather than queried back from either upstream service.
 *
 * <h2>Why {@code put}, not accumulate</h2>
 *
 * Each {@code producer.output} record and each {@code customer.demand} record already carries a
 * plant's or a zone's <em>total</em> figure for that tick, not a delta -- exactly like
 * {@code PowerPlant.currentOutputMw} and a tick's {@code ZoneDemand} are both absolute snapshots.
 * Recording one therefore replaces what was known about that plant or zone, rather than adding to
 * it.
 *
 * <h2>Units</h2>
 *
 * Producer publishes megawatts; Customer publishes kilowatts. {@link #totalSupplyKw()} converts,
 * so every total this class hands out is in kW -- mixing the two here once is safer than trusting
 * every caller to remember which unit which map holds.
 *
 * <h2>A known limitation: stale entries never leave</h2>
 *
 * Neither upstream service publishes a "this plant/zone is gone" record: {@code GenerationService}
 * simply omits a deactivated plant from a tick's events, and {@code DemandSimulator} does the same
 * for a deleted zone. A plant or zone that stops publishing therefore leaves its last known figure
 * here forever, gently overstating whichever total it belonged to. Acceptable for a simulation
 * where fleet and zone membership rarely change mid-run; a real deployment would need eviction on
 * a signal neither upstream event currently carries.
 */
@Component
public class GridStateTracker {

    private static final double KW_PER_MW = 1000.0;

    private final ConcurrentHashMap<Long, Double> outputMwByPlant = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> demandKwByZone = new ConcurrentHashMap<>();

    public void recordSupply(long plantId, double outputMw) {
        outputMwByPlant.put(plantId, outputMw);
    }

    public void recordDemand(String zoneId, double demandKw) {
        demandKwByZone.put(zoneId, demandKw);
    }

    /** Sum of every plant's latest known output, converted to kW. */
    public double totalSupplyKw() {
        return outputMwByPlant.values().stream().mapToDouble(Double::doubleValue).sum() * KW_PER_MW;
    }

    /** Sum of every zone's latest known demand, already in kW. */
    public double totalDemandKw() {
        return demandKwByZone.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
