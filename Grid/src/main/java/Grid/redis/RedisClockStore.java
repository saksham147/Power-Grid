package Grid.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import Grid.simulation.SimulationClock;

/**
 * Publishes the current tick to Redis, so anything -- a REST client, a future service, a human at
 * {@code redis-cli} -- can read "what time is it" without joining the Kafka stream from wherever it
 * happens to start.
 *
 * <h2>Why it cannot slow the clock down</h2>
 *
 * If Grid's own tick loop blocked on this write, a slow or unreachable Redis would stretch the
 * interval between ticks -- and every other service's pace is derived from this one loop. The write
 * is issued reactively and subscribed without waiting, exactly like {@code RedisDemandStateStore} in
 * Customer, for the same reason.
 *
 * <h2>Resuming across a restart</h2>
 *
 * {@link #lastKnownTick} blocks, deliberately: it runs once, at boot, before the loop starts, so
 * there is nothing yet for it to delay.
 */
@Component
public class RedisClockStore {

    public static final String KEY = "grid:clock";

    private static final Logger log = LoggerFactory.getLogger(RedisClockStore.class);
    private static final long LOG_EVERY = 100;

    private final ReactiveStringRedisTemplate redis;
    private final int resumeAttempts;
    private final Duration resumeRetryDelay;
    private final AtomicLong failures = new AtomicLong();

    public RedisClockStore(ReactiveStringRedisTemplate redis,
            @Value("${grid.clock.resume-attempts:10}") int resumeAttempts,
            @Value("${grid.clock.resume-retry-delay:PT3S}") Duration resumeRetryDelay) {
        this.redis = redis;
        this.resumeAttempts = Math.max(1, resumeAttempts);
        this.resumeRetryDelay = resumeRetryDelay;
    }

    public void save(long tick, double frequencyDeviation, Instant at) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("tick", Long.toString(tick));
        fields.put("simulatedTime", SimulationClock.formatTimeOfDay(tick));
        fields.put("simulatedDay", Long.toString(SimulationClock.dayNumber(tick)));
        fields.put("frequencyDeviation", Double.toString(frequencyDeviation));
        fields.put("updatedAt", at.toString());

        redis.opsForHash()
                .putAll(KEY, fields)
                .subscribe(written -> {
                }, this::recordFailure);
    }

    /**
     * The last tick this or a previous instance of Grid wrote, or 0 if Redis has nothing (a fresh
     * install) or stayed unreachable through every retry. Blocking is safe here: called once, at
     * boot, before the tick loop starts, so there is nothing yet for it to delay -- unlike {@link
     * #save}, which must never wait on Redis once ticking has begun.
     *
     * <h2>Why it retries instead of degrading at the first failure</h2>
     *
     * A failed read used to mean "start clean at tick 0", on the theory that was a safe degrade.
     * It is not: Billing and Distributor each refuse a (zone, tick) they have already recorded --
     * the idempotency guard against Kafka redelivery -- so a clock that restarts from 0 walks back
     * through tick numbers those services already hold, and they silently record nothing until it
     * climbs past their history again (about a day, after a few days of running). A Redis that is
     * merely slow at the moment Grid boots -- the usual case after the whole stack comes up at
     * once, or Redis is restarted -- must not be able to do that, so the read is retried for a
     * bounded time first. Only a Redis that stays down through every attempt falls back to 0.
     */
    public long lastKnownTick() {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= resumeAttempts; attempt++) {
            try {
                return readTick();
            } catch (RuntimeException e) {
                lastFailure = e;
                if (attempt < resumeAttempts) {
                    log.warn("Could not read the last known tick from Redis (attempt {} of {}); retrying in {}: {}",
                            attempt, resumeAttempts, resumeRetryDelay, e.toString());
                    if (!pause()) {
                        break;
                    }
                }
            }
        }
        log.error("Could not read the last known tick from Redis after {} attempt(s); STARTING AT TICK 0. "
                + "Billing and Distributor will ignore ticks they already recorded until the clock passes "
                + "their history -- restore grid:clock and restart Grid to recover.", resumeAttempts, lastFailure);
        return 0L;
    }

    /** One blocking read of the saved tick; 0 if the key does not exist yet. Throws if Redis fails. */
    long readTick() {
        Map<String, String> fields = redis.<String, String>opsForHash().entries(KEY)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .block();
        String tick = fields == null ? null : fields.get("tick");
        return tick == null ? 0L : Long.parseLong(tick);
    }

    /** @return false if interrupted, in which case the caller should stop retrying */
    private boolean pause() {
        try {
            Thread.sleep(resumeRetryDelay.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void recordFailure(Throwable error) {
        long count = failures.incrementAndGet();
        if (count == 1 || count % LOG_EVERY == 0) {
            log.error("Failed to write the clock to Redis ({} failures so far); ticking continues",
                    count, error);
        }
    }

    /** Total write failures since startup. */
    public long failureCount() {
        return failures.get();
    }
}
