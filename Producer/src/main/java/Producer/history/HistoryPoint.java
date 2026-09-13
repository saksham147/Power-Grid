package Producer.history;

import java.time.Instant;

import Producer.model.GenerationRecord;
import Producer.model.GenerationRollup;
import Producer.simulation.SimulationClock;

/**
 * One point in a plant's generation history, from either table.
 *
 * <p>
 * Raw and rolled-up rows share this shape so a caller receives a single series,
 * but
 * {@link #resolution} says which it is: a chart must not draw a 5-second reading
 * and an hourly
 * average as if they meant the same thing.
 *
 * @param at          when the reading was taken, or when the bucket began
 * @param resolution  {@code RAW} for one tick, {@code ROLLUP} for one simulated
 *                    hour
 * @param outputMw    the reading, or the bucket's average
 * @param minOutputMw equal to {@code outputMw} for a raw point
 * @param maxOutputMw equal to {@code outputMw} for a raw point
 * @param energyMwh   energy over the point's span; exact in both cases
 * @param samples     ticks this point covers -- 1 raw, normally 12 rolled up
 * @param firstTick   first tick covered
 * @param lastTick    last tick covered
 */
public record HistoryPoint(
        Instant at,
        Resolution resolution,
        double outputMw,
        double minOutputMw,
        double maxOutputMw,
        double energyMwh,
        int samples,
        long firstTick,
        long lastTick) {

    public enum Resolution {
        RAW, ROLLUP
    }

    static HistoryPoint of(GenerationRecord r) {
        return new HistoryPoint(r.getRecordedAt(), Resolution.RAW,
                r.getOutputMw(), r.getOutputMw(), r.getOutputMw(),
                SimulationClock.energyMwh(r.getOutputMw()),
                1, r.getTickNumber(), r.getTickNumber());
    }

    static HistoryPoint of(GenerationRollup r) {
        return new HistoryPoint(r.getBucketStart(), Resolution.ROLLUP,
                r.getAvgOutputMw(), r.getMinOutputMw(), r.getMaxOutputMw(),
                r.getEnergyMwh(),
                r.getSampleCount(), r.getFirstTick(), r.getLastTick());
    }
}
