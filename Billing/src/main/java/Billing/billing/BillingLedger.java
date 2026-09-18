package Billing.billing;

import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port: applies one zone's already-computed billing cycle charge.
 *
 * <p>
 * Deliberately one method, not the two smaller ports ("record the bill" and "debit the wallet")
 * that would otherwise mirror the rest of this project's outbound-port style. A billing charge is
 * one atomic fact -- the record of what was billed and the wallet debit it caused must commit or
 * fail together, or a crash between two separate calls could either charge a wallet with no record
 * of why, or leave a billing record with no debit behind it. Two independently callable ports
 * cannot guarantee that; a single method an adapter can wrap in one {@code @Transactional}
 * boundary can.
 *
 * <p>
 * The idempotency guard against Kafka's at-least-once delivery lives here too, for the same
 * reason: checking "already billed?" and then writing must not be two separate steps another
 * delivery of the same event could interleave with.
 */
public interface BillingLedger {

    /**
     * @param overageKwh        how much of {@code kwh} was above the zone's assigned capacity --
     *                          0 for an uncapped zone; already priced into {@code costRupees}
     * @param overageCostRupees the slice of {@code costRupees} charged at the overage rate, stored
     *                          alongside the total so the surcharge stays visible in billing
     *                          history without having to reconstruct it from a rate that may have
     *                          since changed
     * @return the result, or empty if {@code (zoneId, tick)} was already billed -- a duplicate
     *         delivery of the same event, applied as a no-op rather than a double charge
     */
    Optional<BillingResult> apply(String zoneId, String zoneName, long tick, double kwh, double overageKwh,
            double ratePerKwh, double costRupees, double overageCostRupees, Instant timestamp);
}
