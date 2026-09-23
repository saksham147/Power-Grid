package Grid.history;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import Grid.model.TickRecordRepository;

/**
 * Keeps {@code tick_record} bounded to a rolling wall-clock window -- there is no rollup tier the
 * way Billing/Distributor need for long-term cost totals, since this table only exists to feed a
 * live chart; a row older than the window is just deleted. Mirrors {@code Billing.billing.
 * MaintenanceChargeJob}'s {@code @Scheduled} shape: an ISO-8601 interval string, an {@code enabled}
 * guard, try/catch-swallow so one bad run doesn't cancel every future one. No {@code
 * TransactionTemplate} needed, unlike that job: the single {@code deleteRecordedBefore} call is
 * already transactional on {@code TickRecordRepository}'s own proxy, with nothing else to
 * coordinate around it.
 */
@Component
public class TickHistoryPruneJob {

    private static final Logger log = LoggerFactory.getLogger(TickHistoryPruneJob.class);

    private final TickRecordRepository repository;
    private final Duration rawRetention;
    private final boolean enabled;

    public TickHistoryPruneJob(TickRecordRepository repository,
            @Value("${grid.history.raw-retention:24h}") Duration rawRetention,
            @Value("${grid.history.prune-enabled:true}") boolean enabled) {
        this.repository = repository;
        this.rawRetention = rawRetention;
        this.enabled = enabled;
    }

    int pruneOlderThan(Instant now) {
        return repository.deleteRecordedBefore(now.minus(rawRetention));
    }

    @Scheduled(fixedDelayString = "${grid.history.prune-interval:PT10M}")
    void runScheduled() {
        if (!enabled) {
            return;
        }
        try {
            int deleted = pruneOlderThan(Instant.now());
            if (deleted > 0) {
                log.info("Pruned {} tick history row(s) older than {}", deleted, rawRetention);
            }
        } catch (Exception e) {
            // Swallowed for the same reason every other @Scheduled job in this project does this:
            // an escaping exception cancels all future runs on this scheduler.
            log.error("Tick history prune failed; will retry next interval", e);
        }
    }
}
