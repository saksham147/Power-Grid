package Billing.billing;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Service;

import Billing.model.BillingDailyRollupRepository;
import Billing.model.BillingRecordRepository;

/**
 * Turns cumulative kWh sold, grid-wide, into which plant types are currently purchasable. The
 * running total is raw {@link BillingRecordRepository#sumKwh()} plus already-rolled-up {@link
 * BillingDailyRollupRepository#sumTotalKwh()} -- a rollup replaces the raw rows it summarises
 * rather than sitting alongside them, so this never double-counts.
 */
@Service
public class UnlockService {

    private final BillingRecordRepository billingRecords;
    private final BillingDailyRollupRepository rollups;

    public UnlockService(BillingRecordRepository billingRecords, BillingDailyRollupRepository rollups) {
        this.billingRecords = billingRecords;
        this.rollups = rollups;
    }

    public double cumulativeKwhSold() {
        return billingRecords.sumKwh() + rollups.sumTotalKwh();
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
