package Customer.infrastructure.redis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import Customer.application.DemandSnapshot;
import Customer.application.DemandStateStore;
import Customer.domain.ZoneDemand;

/**
 * Records the latest demand in Redis.
 *
 * <h2>One round trip per tick</h2>
 *
 * Every zone plus the tick metadata goes into a single hash in one
 * {@code HSET}, so the cost is
 * one round trip whether there are three zones or three hundred. A key per zone
 * would have turned
 * a tick into N round trips for no benefit -- nothing reads a zone in isolation.
 *
 * <h2>Why it cannot slow the simulation down</h2>
 *
 * The write is issued reactively and subscribed without waiting, so the calling
 * thread returns
 * immediately and a slow or unreachable Redis costs the snapshot rather than the
 * tick. This is
 * the one place the reactive driver genuinely earns its place: the blocking
 * template would put
 * the connection timeout directly into the tick's critical path.
 *
 * <h2>What is stored, and what is not</h2>
 *
 * Latest demand figures only. Zone names and customer counts are configuration
 * and are on every
 * Kafka record already; history belongs to the event stream. A cache that tried
 * to be either
 * would be a worse version of something that already exists.
 */
@Component
public class RedisDemandStateStore implements DemandStateStore {

    /** Single key; zone fields are prefixed so a zone id can never collide with a metadata field. */
    public static final String KEY = "customer:demand:latest";
    private static final String ZONE_FIELD_PREFIX = "zone:";

    private static final Logger log = LoggerFactory.getLogger(RedisDemandStateStore.class);
    private static final long LOG_EVERY = 100;

    private final ReactiveStringRedisTemplate redis;
    private final AtomicLong failures = new AtomicLong();

    public RedisDemandStateStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(DemandSnapshot snapshot) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ZoneDemand zone : snapshot.zones()) {
            fields.put(ZONE_FIELD_PREFIX + zone.zoneId(), format(zone.demandKw()));
        }
        fields.put("tick", Long.toString(snapshot.tick()));
        fields.put("simulatedTime", snapshot.simulatedTime());
        fields.put("totalKw", format(snapshot.totalKw()));
        fields.put("updatedAt", snapshot.at().toString());

        redis.opsForHash()
                .putAll(KEY, fields)
                .subscribe(written -> {
                }, this::recordFailure);
    }

    /** Fixed to one decimal: kW below that is noise, and it keeps the value readable in redis-cli. */
    private static String format(double kw) {
        return "%.1f".formatted(kw);
    }

    private void recordFailure(Throwable error) {
        long count = failures.incrementAndGet();
        if (count == 1 || count % LOG_EVERY == 0) {
            log.error("Failed to write demand state to Redis ({} failures so far); the simulation continues",
                    count, error);
        }
    }

    /** Total state-write failures since startup. */
    public long failureCount() {
        return failures.get();
    }
}
