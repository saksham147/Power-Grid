package Grid.redis;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final AtomicLong failures = new AtomicLong();

    public RedisClockStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
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
     * install) or is unreachable. Blocking is safe here: called once, at boot, before the tick loop
     * starts, so there is nothing yet for it to delay -- unlike {@link #save}, which must never wait
     * on Redis once ticking has begun.
     */
    public long lastKnownTick() {
        try {
            Map<String, String> fields = redis.<String, String>opsForHash().entries(KEY)
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                    .block();

            String tick = fields == null ? null : fields.get("tick");
            return tick == null ? 0L : Long.parseLong(tick);
        } catch (RuntimeException e) {
            // An unreachable Redis at boot is not fatal: starting clean at tick 0 is a safe
            // degrade, and every later tick's write will keep retrying on its own.
            log.error("Could not read the last known tick from Redis; starting at tick 0", e);
            return 0L;
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
