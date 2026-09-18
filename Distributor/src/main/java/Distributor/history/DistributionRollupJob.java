package Distributor.history;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import Distributor.model.DistributionDailyRollupRepository;
import Distributor.model.DistributionRecordRepository;

/**
 * Compresses raw per-tick balance history once it ages past {@code
 * distributor.history.raw-retention-days} <em>simulated</em> days.
 *
 * <h2>Simulated days, not wall-clock time</h2>
 *
 * Unlike {@code Producer.history.GenerationRollupJob} (which keys retention off {@code
 * recorded_at}), the cutoff here is a tick number: {@code latestTick - rawRetentionDays * 288}.
 * "Keep the last 15 simulation days" has to mean 15 days of simulated history regardless of how
 * much real time that took to arrive -- a dev session left idle overnight, or a service restarted
 * mid-run, must not silently change how far back the raw data reaches.
 *
 * <h2>Rolling, not end-of-day</h2>
 *
 * Every {@code rollup-interval} of real time, it rolls up whatever has aged out since the last run,
 * rather than compressing a simulated day the moment it ends. The last {@code rawRetentionDays}
 * simulated days therefore stay raw at every moment, a missed run is absorbed by the next one with
 * no catch-up logic, and each transaction moves a bounded slice of rows instead of an unbounded one.
 *
 * <h2>Why a run can never double-count or lose a row</h2>
 *
 * The summarising insert and the delete run in one transaction against the same {@code cutoffTick},
 * computed once in Java rather than re-derived by each statement -- a crash rolls both back, and
 * the next run retries from the same, still-raw state. The unique key on {@code (zone_id,
 * simulated_day)} turns any duplicate that got through anyway into a failed run rather than
 * silently doubled data -- the same reasoning {@code GenerationRollupJob} documents for its own
 * insert-then-delete pair.
 *
 * <h2>Why {@code @Scheduled}, not Producer's dedicated {@code TaskScheduler}</h2>
 *
 * Producer pins its rollup to its own single simulation thread specifically so a rollup never runs
 * concurrently with that thread's own tick-insert into the same table. Distributor has no such
 * thread -- it is purely Kafka-listener-driven, already concurrent by design -- so there is no
 * matching constraint to respect, and Spring's own scheduler is enough.
 */
@Component
public class DistributionRollupJob {

    private static final Logger log = LoggerFactory.getLogger(DistributionRollupJob.class);

    /**
     * Ticks in one simulated day. Not read from {@code Grid.simulation.SimulationClock} directly --
     * there is no shared library module between services, so, like every other cross-service
     * constant in this project, it is a local copy that must be kept numerically identical to the
     * canonical one in Grid.
     */
    static final long TICKS_PER_DAY = 288;

    private final DistributionRecordRepository records;
    private final DistributionDailyRollupRepository rollups;
    private final TransactionTemplate transactions;
    private final DistributionHistoryProperties properties;

    public DistributionRollupJob(DistributionRecordRepository records, DistributionDailyRollupRepository rollups,
            TransactionTemplate transactions, DistributionHistoryProperties properties) {
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
    @Scheduled(fixedDelayString = "${distributor.history.rollup-interval:PT10M}")
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
            log.error("Distribution history rollup failed; raw rows are kept and the next run will retry", e);
        }
    }
}
