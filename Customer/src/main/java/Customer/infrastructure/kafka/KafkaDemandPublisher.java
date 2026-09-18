package Customer.infrastructure.kafka;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import Customer.application.DemandPublisher;
import Customer.application.DemandSnapshot;
import Customer.domain.ZoneDemand;

/**
 * Publishes zone demand to {@code customer.demand}.
 *
 * <h2>Why this cannot stall the simulation</h2>
 *
 * {@code KafkaTemplate.send} is asynchronous in the happy path -- it appends to
 * the producer's
 * record accumulator and returns -- but it is <em>not</em> unconditionally
 * non-blocking. It waits
 * on topic metadata, and on buffer space when the accumulator is full, for up to
 * {@code max.block.ms}. That defaults to <b>60 seconds</b>, which on a five
 * second tick would mean
 * an unreachable broker silently freezes the whole simulation rather than
 * costing it events.
 *
 * <p>
 * The producer is therefore configured with a bounded {@code max.block.ms} and a
 * short
 * {@code delivery.timeout.ms}; see {@code application.yml}. Old demand is worth
 * dropping rather
 * than retrying, because by the time a long retry succeeds the figure describes a
 * moment that has
 * already passed and a fresher one has replaced it.
 *
 * <p>
 * That bound is per {@code send}, not per tick, so a tick of N zones against a
 * dead broker would
 * cost N times it -- twenty zones would overrun the tick itself. The loop
 * therefore works to a
 * publish budget and abandons the rest of a tick once it is spent. Those records
 * were going to
 * fail anyway; what matters is that the simulation keeps its cadence.
 *
 * <p>
 * Failures are counted and logged, never thrown.
 */
@Component
public class KafkaDemandPublisher implements DemandPublisher {

    public static final String TOPIC = "customer.demand";

    private static final Logger log = LoggerFactory.getLogger(KafkaDemandPublisher.class);

    /** Log the first failure, then one in every batch of this many, so an outage cannot flood the log. */
    private static final long LOG_EVERY = 100;

    /**
     * How long a tick may spend publishing before it gives up on the remaining zones.
     * Comfortably
     * inside a tick, and never reached while the broker is healthy -- an enqueue is
     * measured in
     * microseconds.
     */
    private static final long PUBLISH_BUDGET_NANOS = 1_000_000_000L;

    private final KafkaTemplate<String, ZoneDemandEvent> kafkaTemplate;
    private final AtomicLong failures = new AtomicLong();

    public KafkaDemandPublisher(KafkaTemplate<String, ZoneDemandEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(DemandSnapshot snapshot) {
        long deadline = System.nanoTime() + PUBLISH_BUDGET_NANOS;
        int published = 0;

        for (ZoneDemand zone : snapshot.zones()) {
            if (System.nanoTime() > deadline) {
                recordFailure(zone.zoneId(),
                        new IllegalStateException("publish budget spent; %d of %d zones abandoned on tick %d"
                                .formatted(snapshot.zones().size() - published, snapshot.zones().size(),
                                        snapshot.tick())));
                return;
            }

            ZoneDemandEvent event = new ZoneDemandEvent(
                    zone.zoneId(),
                    zone.name(),
                    snapshot.tick(),
                    snapshot.simulatedTime(),
                    zone.unitCount(),
                    zone.demandKw(),
                    snapshot.at());

            send(event);
            published++;
        }
    }

    private void send(ZoneDemandEvent event) {
        try {
            // Keyed by zone id: one zone's history stays on one partition and stays
            // ordered, while three partitions still let consumers parallelise across zones.
            // The whole tick's records share a linger window, so they leave as one request.
            kafkaTemplate.send(TOPIC, event.zoneId(), event)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            recordFailure(event.zoneId(), error);
                        }
                    });
        } catch (RuntimeException e) {
            // Measured: an unreachable broker makes send() return a *failed future* after
            // max.block.ms rather than throwing, so whenComplete above is the usual path.
            // This catches what does throw synchronously -- a serialisation fault, or the
            // producer being closed underneath us.
            recordFailure(event.zoneId(), e);
        }
    }

    private void recordFailure(String zoneId, Throwable error) {
        long count = failures.incrementAndGet();
        if (count == 1 || count % LOG_EVERY == 0) {
            log.error("Failed to publish demand for zone {} ({} failures so far); the simulation continues",
                    zoneId, count, error);
        }
    }

    /** Total publish failures since startup. */
    public long failureCount() {
        return failures.get();
    }
}
