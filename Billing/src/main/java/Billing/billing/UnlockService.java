package Billing.billing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Service;

import Billing.model.BillingHypertableSetup;
import Billing.model.BillingRecordRepository;
import Billing.model.BillingRollupQuery;

/**
 * Turns cumulative kWh sold, grid-wide, into which plant types are currently purchasable. The
 * running total is raw {@link BillingRecordRepository#sumKwhSince} plus already-rolled-up {@link
 * BillingRollupQuery#sumTotalKwhBefore}, split at the same cutoff both queries share.
 *
 * <h2>Why a cutoff now, where this used to sum both tables unconditionally</h2>
 *
 * It used to be true that "a rollup replaces the raw rows it summarises rather than sitting
 * alongside them" -- a scheduled job moved rows from one table to the other inside one
 * transaction, so a tick was in exactly one of them at any moment. That is no longer true:
 * {@code billing_record} is a TimescaleDB hypertable now and {@code billing_daily_rollup} a
 * continuous aggregate over it, each maintained by its own independent background policy
 * (retention, and aggregate refresh). A tick can be -- and routinely is, for a while -- counted in
 * both at once. Summing both sides unconditionally would double it.
 *
 * <p>
 * So this computes one cutoff, {@link BillingHypertableSetup#RAW_RETENTION} back from now,
 * <b>truncated to a whole day</b> to match the aggregate's own day-wide buckets: a day's bucket
 * that straddled an untruncated cutoff would be counted whole by the rollup side while its second
 * half was also counted by the raw side -- verified against this exact database: an untruncated
 * cutoff over-counted the true total by roughly 11% the first time this was tried. Truncating to
 * the bucket width first is the same fix a finer-grained version of this problem needs wherever a
 * bucketed aggregate and its raw source can both hold the same instant at once.
 */
@Service
public class UnlockService {

    private final BillingRecordRepository billingRecords;
    private final BillingRollupQuery rollups;

    public UnlockService(BillingRecordRepository billingRecords, BillingRollupQuery rollups) {
        this.billingRecords = billingRecords;
        this.rollups = rollups;
    }

    public double cumulativeKwhSold() {
        Instant cutoffDay = Instant.now().minus(BillingHypertableSetup.RAW_RETENTION).truncatedTo(ChronoUnit.DAYS);
        return billingRecords.sumKwhSince(cutoffDay) + rollups.sumTotalKwhBefore(cutoffDay);
    }

    public boolean isUnlocked(PlantType type) {
        return UnlockThresholds.isUnlocked(type, cumulativeKwhSold());
    }

    /** The next type still locked, ordered by threshold, or empty if every type is unlocked. */
    public Optional<PlantType> nextLocked() {
        double sold = cumulativeKwhSold();
        return Arrays.stream(PlantType.values())
                .filter(type -> !UnlockThresholds.isUnlocked(type, sold))
                .min(Comparator.comparingDouble(UnlockThresholds::thresholdFor));
    }
}
