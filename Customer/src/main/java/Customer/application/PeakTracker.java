package Customer.application;

import java.time.LocalTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Per-zone running maxima for today's demand, split into a daytime peak and a nighttime peak --
 * kept in memory, not Redis or a table, the same "cheap, rebuildable, restart loses it" choice
 * {@code Grid.simulation.GridStateTracker} makes for its own in-memory maps. A restart starts both
 * peaks back at zero for the rest of today rather than recovering them, which is an acceptable
 * gap for a dashboard readout that rebuilds within a few ticks either way.
 *
 * <p>
 * Reset happens on the first {@link #record} call that reports a new simulated day number, not on
 * a wall-clock schedule -- matching how every other day boundary in this project is counted in
 * simulated, not wall-clock, days.
 */
@Component
public class PeakTracker {

    /** 06:00 to 18:00 is "day"; everything else is "night". A coarse bucket layered on top of
     *  {@code DemandProfile}'s own finer hourly shape, not a replacement for it. */
    private static final LocalTime DAY_START = LocalTime.of(6, 0);
    private static final LocalTime DAY_END = LocalTime.of(18, 0);

    private record Peaks(double dayPeakKw, double nightPeakKw) {
        static final Peaks ZERO = new Peaks(0.0, 0.0);
    }

    private final ConcurrentHashMap<String, Peaks> peaksByZone = new ConcurrentHashMap<>();
    private volatile long currentDay = -1;

    public void record(String zoneId, double demandKw, LocalTime time, long dayNumber) {
        if (dayNumber != currentDay) {
            // A new simulated day for the fleet: every zone's peaks restart together, exactly
            // once per day regardless of how many zones call record() for that same day.
            peaksByZone.clear();
            currentDay = dayNumber;
        }

        boolean isDaytime = !time.isBefore(DAY_START) && time.isBefore(DAY_END);
        peaksByZone.compute(zoneId, (id, peaks) -> {
            Peaks current = peaks != null ? peaks : Peaks.ZERO;
            return isDaytime
                    ? new Peaks(Math.max(current.dayPeakKw(), demandKw), current.nightPeakKw())
                    : new Peaks(current.dayPeakKw(), Math.max(current.nightPeakKw(), demandKw));
        });
    }

    public double dayPeakKw(String zoneId) {
        return peaksByZone.getOrDefault(zoneId, Peaks.ZERO).dayPeakKw();
    }

    public double nightPeakKw(String zoneId) {
        return peaksByZone.getOrDefault(zoneId, Peaks.ZERO).nightPeakKw();
    }
}
