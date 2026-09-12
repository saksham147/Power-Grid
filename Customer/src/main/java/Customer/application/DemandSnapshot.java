package Customer.application;

import java.time.Instant;
import java.util.List;

import Customer.domain.ZoneDemand;

/**
 * One tick's worth of demand: every zone, plus the context they share.
 *
 * <p>
 * Tick number, simulated time and wall-clock instant are identical across a
 * tick's zones, so they
 * live here rather than being repeated on each {@link ZoneDemand}. Giving the
 * whole tick a name is
 * also what lets both output ports take a single argument, which is how batching
 * gets enforced by
 * the type rather than left to an adapter's discretion.
 *
 * @param tick          the tick this covers
 * @param simulatedTime simulated time of day as {@code "HH:mm"}
 * @param at            wall-clock instant the tick ran, shared by every record it
 *                      produces so they can be
 *                      correlated across Redis and Kafka
 * @param zones         per-zone aggregates
 * @param totalKw       sum across zones, computed once in {@link #of}
 */
public record DemandSnapshot(
        long tick,
        String simulatedTime,
        Instant at,
        List<ZoneDemand> zones,
        double totalKw) {

    /**
     * Builds a snapshot, summing the zones. The total is derived here and nowhere
     * else, so it
     * cannot drift out of step with the list it describes.
     */
    public static DemandSnapshot of(long tick, String simulatedTime, Instant at, List<ZoneDemand> zones) {
        return new DemandSnapshot(
                tick,
                simulatedTime,
                at,
                List.copyOf(zones),
                zones.stream().mapToDouble(ZoneDemand::demandKw).sum());
    }
}
