package Grid.model;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import Grid.history.TickHistoryRecorder;

/**
 * The {@link TickHistoryRecorder} adapter: checks the idempotency guard, then inserts. Called
 * directly from {@link Grid.simulation.GridClockRunner#tickOnce}, once per tick, right after the
 * tick's Kafka publish and Redis save -- both of which have already taken effect by the time this
 * runs, so a failure here can only cost one history data point, never the tick itself.
 *
 * <p>
 * That is also why this swallows its own failures rather than letting them propagate: {@code
 * tickOnce()}'s caller already logs and continues on any exception (a single bad tick must not
 * stop the whole system's clock), but a message there would read "Tick failed" for what was really
 * just a history write hiccup. Logging it here, specifically, is more honest about what broke.
 */
@Component
public class JpaTickHistoryRecorder implements TickHistoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(JpaTickHistoryRecorder.class);

    private final TickRecordRepository repository;

    public JpaTickHistoryRecorder(TickRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(long tickNumber, String simulatedTime, long simulatedDay, double frequencyDeviation,
            double totalSupplyKw, double totalDemandKw, boolean loadExceeded, Instant recordedAt) {
        try {
            if (repository.existsByTickNumber(tickNumber)) {
                return;
            }
            repository.save(new TickRecord(tickNumber, simulatedTime, simulatedDay, frequencyDeviation,
                    totalSupplyKw, totalDemandKw, loadExceeded, recordedAt));
        } catch (Exception e) {
            log.error("Could not record tick {} for the history chart; the clock continues", tickNumber, e);
        }
    }
}
