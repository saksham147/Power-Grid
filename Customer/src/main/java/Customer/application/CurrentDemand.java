package Customer.application;

import java.time.Instant;
import java.util.Map;

/**
 * The latest demand, as recovered from {@link DemandStateStore#current}.
 *
 * <p>
 * Deliberately thinner than {@link DemandSnapshot}: zone name and customer count never reach the
 * store in the first place (see {@code RedisDemandStateStore}'s javadoc for why), so this carries
 * only what a read can actually recover -- zone id and demand. A caller that wants the full picture
 * joins {@link #demandByZoneId} against the zone configuration itself.
 *
 * @param tick           the tick this reflects; 0 if nothing has been saved yet
 * @param updatedAt      when that tick ran; {@code null} if nothing has been saved yet
 * @param totalKw        sum across zones at that tick
 * @param demandByZoneId each zone's demand in kW, keyed by zone id
 */
public record CurrentDemand(long tick, Instant updatedAt, double totalKw, Map<String, Double> demandByZoneId) {

    private static final CurrentDemand NONE = new CurrentDemand(0, null, 0.0, Map.of());

    /** Before the first tick, or a fresh install with nothing in the store yet. */
    public static CurrentDemand none() {
        return NONE;
    }
}
