package Customer.api;

import java.time.Instant;
import java.util.List;

/**
 * The whole picture as of the latest tick: every configured zone, plus the context they share.
 *
 * @param tick          the tick this reflects; 0 if nothing has run yet
 * @param simulatedTime time of day that tick represents, {@code "HH:mm"}
 * @param simulatedDay  simulated days elapsed since this installation's first tick
 * @param updatedAt     wall-clock instant that tick ran; {@code null} if nothing has run yet
 * @param totalKw       sum across every zone
 * @param zones         every configured zone, in configuration order
 */
public record DemandResponse(
        long tick,
        String simulatedTime,
        long simulatedDay,
        Instant updatedAt,
        double totalKw,
        List<ZoneDemandResponse> zones) {
}
