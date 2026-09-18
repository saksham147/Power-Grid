package Billing.billing;

import java.time.Instant;

/**
 * One zone's billing cycle outcome: what it consumed, what it cost, and the wallet balance after
 * the charge was applied.
 *
 * @param overageKwh        how much of {@code kwh} was above the zone's assigned capacity (0 for
 *                           an uncapped zone, or one drawing within its capacity) -- see {@code
 *                           BillingCycleService}
 * @param overageCostRupees the portion of {@code costRupees} charged at the overage rate; already
 *                           included in {@code costRupees}, broken out here for transparency
 */
public record BillingResult(
        String zoneId,
        String zoneName,
        long tick,
        double kwh,
        double overageKwh,
        double ratePerKwh,
        double costRupees,
        double overageCostRupees,
        double balanceAfterRupees,
        Instant timestamp) {
}
