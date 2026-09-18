package Billing.history;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import Billing.model.BillingDailyRollupRepository;
import Billing.model.BillingRecordRepository;

/**
 * Compresses raw per-tick billing history once it ages past {@code
 * billing.history.raw-retention-days} <em>simulated</em> days. Mirrors {@code
 * Distributor.history.DistributionRollupJob} exactly -- same simulated-day cutoff math, same
 * single-transaction insert-then-delete, same {@code @Scheduled} choice over Producer's dedicated
 * simulation-thread scheduler, since Billing is just as purely Kafka-listener-driven as Distributor
 * is. See that class for the full reasoning; this class only restates what differs.
 */
@Component
public class BillingRollupJob {

    private static final Logger log = LoggerFactory.getLogger(BillingRollupJob.class);

    /**
     * Ticks in one simulated day. Not read from {@code Grid.simulation.SimulationClock} directly --
     * there is no shared library module between services, so, like every other cross-service
     * constant in this project, it is a local copy that must be kept numerically identical to the
     * canonical one in Grid.
     */
    static final long TICKS_PER_DAY = 288;

    private final BillingRecordRepository records;
    private final BillingDailyRollupRepository rollups;
    private final TransactionTemplate transactions;
    private final BillingHistoryProperties properties;

    public BillingRollupJob(BillingRecordRepository records, BillingDailyRollupRepository rollups,
            TransactionTemplate transactions, BillingHistoryProperties properties) {
        this.records = records;
        this.rollups = rollups;
        this.transactions = transactions;
        this.properties = properties;
    }

    /**
     * Rolls up and removes every raw row at or before {@code latestTick - rawRetentionDays * 288}.
     *
     * @param latestTick the highest tick number currently on record; the reference point retention
     *                   counts back from, so tests can place it exactly
     * @return empty if there is nothing to roll (an empty table, or nothing has aged out yet)
     */
    public Optional<RollupResult> rollUp(long latestTick) {
        long cutoffTick = latestTick - properties.rawRetentionDays() * TICKS_PER_DAY;
        if (cutoffTick <= 0) {
            return Optional.empty();
        }

        return Optional.of(transactions.execute(status -> {
            int buckets = rollups.rollUpBefore(cutoffTick, TICKS_PER_DAY);
            int deleted = records.deleteBefore(cutoffTick);
            return new RollupResult(cutoffTick, buckets, deleted);
        }));
    }

    /** No {@code initialDelay}: the first run fires right after startup, the same "absorb any
     *  backlog left while the service was down" intent {@code GenerationRollupJob} documents,
     *  rather than leaving a large existing backlog sitting raw for a full interval. */
    @Scheduled(fixedDelayString = "${billing.history.rollup-interval:PT10M}")
    void runScheduled() {
        if (!properties.rollupEnabled()) {
            return;
        }
        try {
            Long latestTick = records.findMaxTickNumber();
            if (latestTick == null) {
                return;
            }
            rollUp(latestTick).ifPresent(result -> {
                if (result.rawDeleted() > 0) {
                    log.info("Rolled {} raw rows into {} zone-day buckets (before tick {})",
                            result.rawDeleted(), result.bucketsWritten(), result.cutoffTick());
                }
            });
        } catch (Exception e) {
            // Swallowed so the schedule survives: an exception escaping a @Scheduled method
            // cancels future runs. The raw rows are untouched by the rolled-back transaction and
            // simply wait for the next run.
            log.error("Billing history rollup failed; raw rows are kept and the next run will retry", e);
        }
    }
}
