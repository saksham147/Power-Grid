package Grid.event;

import java.time.Instant;

/**
 * One zone's merged supply-and-demand picture for a tick, consumed from
 * {@code distributor.zone-balance}.
 *
 * <p>
 * A structural, not a Java, contract: this deliberately mirrors {@code Distributor.event.
 * ZoneBalanceEvent} field-for-field rather than sharing a class file -- there is no shared library
 * module between the services. Grid only reads {@link #zoneId} and {@link #demandKw}, but the full
 * shape is mirrored anyway so this stays a faithful copy of the wire contract rather than a
 * partial one that silently drifts out of sync with it.
 */
public record ZoneBalanceEvent(
        String zoneId,
        String zoneName,
        long tick,
        double demandKw,
        double suppliedKw,
        double balanceKw,
        Instant timestamp) {
}
