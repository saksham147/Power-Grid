package Producer.history;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import Producer.model.GenerationRecordRepository;
import Producer.model.GenerationRollupRepository;
import Producer.simulation.SimulationClock;

/**
 * Compresses raw generation history once it ages past
 * {@code producer.history.raw-retention}.
 *
 * <h2>Rolling, not end-of-day</h2>
 *
 * Every {@code rollup-interval} it rolls up whatever is older than the retention,
 * rather than
 * compressing a day at midnight. The last 24 hours therefore stay raw at every
 * moment, a missed
 * run (the service down at midnight, say) is absorbed by the next one with no
 * catch-up logic, and
 * each transaction moves minutes of rows instead of a day's.
 *
 * <h2>Why a run can never double-count or lose a row</h2>
 *
 * <ul>
 * <li>The summarising insert and the delete run in <b>one transaction</b> with
 * one cutoff. A crash
 * rolls both back; the next run retries from the same state.</li>
 * <li>The cutoff is <b>truncated to a whole minute</b>, so a bucket is only ever
 * rolled once and
 * complete. An untruncated cutoff would split a minute across two runs and write
 * two rows for
 * it.</li>
 * <li>A unique key on {@code (plant_id, bucket_start)} turns any duplicate that got
 * through anyway
 * into a failed run rather than silently doubled energy.</li>
 * </ul>
 *
 * <p>
 * The transaction is opened with a {@link TransactionTemplate} rather than
 * {@code @Transactional}.
 * The scheduled task calls this bean's own method, and a self-call bypasses
 * Spring's
 * transactional proxy -- the insert and the delete would each have committed on
 * their own, which
 * is exactly the partial state the design exists to rule out.
 */
@Component
public class GenerationRollupJob {

    private static final Logger log = LoggerFactory.getLogger(GenerationRollupJob.class);

    private final GenerationRecordRepository records;
    private final GenerationRollupRepository rollups;
    private final TransactionTemplate transactions;
    private final TaskScheduler scheduler;
    private final HistoryProperties properties;

    public GenerationRollupJob(GenerationRecordRepository records,
            GenerationRollupRepository rollups,
            TransactionTemplate transactions,
            @Qualifier("simulationTaskScheduler") TaskScheduler scheduler,
            HistoryProperties properties) {
        this.records = records;
        this.rollups = rollups;
        this.transactions = transactions;
        this.scheduler = scheduler;
        this.properties = properties;
    }

    /**
     * Scheduled on the simulation's single-thread scheduler rather than a new one, so
     * a rollup never
     * runs concurrently with a tick inserting into the same table. A run moves only
     * the rows that aged
     * out in the last interval, so the tick it delays waits milliseconds.
     *
     * <p>
     * The first run is immediate, which is what absorbs any backlog left while the
     * service was down.
     */
    @EventListener(ApplicationReadyEvent.class)
    void startOnBoot() {
        if (!properties.rollupEnabled()) {
            log.info("History rollup is disabled");
            return;
        }

        scheduler.scheduleWithFixedDelay(this::runSafely, properties.rollupInterval());
        log.info("History rollup scheduled every {}; raw rows kept for {}",
                properties.rollupInterval(), properties.rawRetention());
    }

    /**
     * Rolls up and removes every raw row recorded before {@code now - rawRetention},
     * truncated to the
     * minute.
     *
     * @param now the reference instant; a parameter so tests can place the cutoff
     *            exactly
     */
    public RollupResult rollUp(Instant now) {
        Instant cutoff = now.minus(properties.rawRetention()).truncatedTo(ChronoUnit.MINUTES);

        return transactions.execute(status -> {
            int buckets = rollups.rollUpRecordedBefore(cutoff, SimulationClock.SIMULATED_MINUTES_PER_TICK);
            int deleted = records.deleteRecordedBefore(cutoff);
            return new RollupResult(cutoff, buckets, deleted);
        });
    }

    private void runSafely() {
        try {
            RollupResult result = rollUp(Instant.now());
            if (result.rawDeleted() > 0) {
                log.info("Rolled {} raw rows into {} buckets (before {})",
                        result.rawDeleted(), result.bucketsWritten(), result.cutoff());
            }
        } catch (Exception e) {
            // Swallowed so the schedule survives: an exception escaping a scheduled task
            // cancels it. The raw rows are untouched by the rolled-back transaction and simply
            // wait for the next run.
            log.error("History rollup failed; raw rows are kept and the next run will retry", e);
        }
    }
}
