package Customer.infrastructure.redis;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import Customer.application.ConsumerUnitRepository;
import Customer.config.CustomerProperties;
import Customer.domain.ConsumerUnit;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Units as data, in Redis -- the exact same shape as {@link RedisZoneRepository}, one level down:
 * one hash, {@code customer:units}, one field per unit keyed by unit id, seeded once from
 * {@code customer.units} in {@code application.yml} and never consulted again after that.
 *
 * <p>
 * {@link #findAll} is called once per tick from {@code DemandSimulator.tick}, on the same Kafka
 * listener thread as {@link RedisZoneRepository#findAll} -- see that class's javadoc for why a
 * blocking read is the right call there.
 */
@Component
public class RedisConsumerUnitRepository implements ConsumerUnitRepository {

    public static final String KEY = "customer:units";

    private static final Logger log = LoggerFactory.getLogger(RedisConsumerUnitRepository.class);
    private static final long LOG_EVERY = 100;

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json;
    private final CustomerProperties seedProperties;
    private final AtomicLong readFailures = new AtomicLong();

    public RedisConsumerUnitRepository(ReactiveStringRedisTemplate redis, ObjectMapper json,
            CustomerProperties seedProperties) {
        this.redis = redis;
        this.json = json;
        this.seedProperties = seedProperties;
    }

    /** See {@link RedisZoneRepository#seedIfEmpty} -- identical reasoning, one level down. */
    @EventListener(ApplicationReadyEvent.class)
    void seedIfEmpty() {
        try {
            Long size = redis.opsForHash().size(KEY).block();
            if (size != null && size > 0) {
                return;
            }

            List<ConsumerUnit> defaults = seedProperties.units().stream()
                    .map(u -> new ConsumerUnit(u.unitId(), u.zoneId(), u.name(), u.type(), u.capacityKw()))
                    .toList();

            if (defaults.isEmpty()) {
                return;
            }

            Map<String, String> fields = defaults.stream()
                    .collect(Collectors.toMap(ConsumerUnit::unitId, this::toJson));

            redis.opsForHash().putAll(KEY, fields).block();
            log.info("Seeded {} units from configuration (Redis had none)", defaults.size());
        } catch (RuntimeException e) {
            log.error("Could not seed units into Redis; starting with an empty fleet until "
                    + "Redis is reachable and a unit is added through the API", e);
        }
    }

    @Override
    public List<ConsumerUnit> findAll() {
        try {
            List<String> values = redis.<String, String>opsForHash().values(KEY).collectList().block();
            return values == null ? List.of() : values.stream().map(this::fromJson).toList();
        } catch (RuntimeException e) {
            long count = readFailures.incrementAndGet();
            if (count == 1 || count % LOG_EVERY == 0) {
                log.error("Failed to read units from Redis ({} failures so far); this tick sees none", count, e);
            }
            return List.of();
        }
    }

    @Override
    public Mono<Void> save(ConsumerUnit unit) {
        return redis.opsForHash().put(KEY, unit.unitId(), toJson(unit)).then();
    }

    @Override
    public Mono<Void> delete(String unitId) {
        return redis.opsForHash().remove(KEY, unitId).then();
    }

    @Override
    public Mono<Void> deleteByZoneId(String zoneId) {
        return redis.<String, String>opsForHash().entries(KEY)
                .filter(entry -> fromJson(entry.getValue()).zoneId().equals(zoneId))
                .map(Map.Entry::getKey)
                .collectList()
                .flatMap(ids -> ids.isEmpty() ? Mono.empty() : redis.opsForHash().remove(KEY, ids.toArray()))
                .then();
    }

    private String toJson(ConsumerUnit unit) {
        return json.writeValueAsString(unit);
    }

    private ConsumerUnit fromJson(String value) {
        return json.readValue(value, ConsumerUnit.class);
    }
}
