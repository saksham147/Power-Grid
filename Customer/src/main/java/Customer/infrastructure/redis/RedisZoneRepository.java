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

import Customer.application.ZoneRepository;
import Customer.config.CustomerProperties;
import Customer.domain.Zone;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Zones as data, in Redis, so a zone can be added or removed at runtime and survive a restart --
 * the static {@code customer.zones} YAML block this replaces could do neither.
 *
 * <h2>Storage</h2>
 *
 * One hash, {@code customer:zones}, one field per zone keyed by zone id, each value the zone's
 * whole configuration as JSON. A zone is small and never queried by anything but id or "all of
 * them", so there is nothing a more granular shape would buy over one blob per zone.
 *
 * <h2>Seeding</h2>
 *
 * On first boot -- the hash does not exist yet -- this seeds it from {@code customer.zones} in
 * {@code application.yml}, so an installation still starts with the same three zones it always did.
 * After that, YAML is never consulted again: the add/delete API is the only way zones change.
 *
 * <h2>Reads are blocking, writes are not</h2>
 *
 * {@link #findAll} is called once per tick from {@link Customer.application.DemandSimulator#tick},
 * a plain synchronous method running on a Kafka listener thread, not a reactive event loop -- so
 * blocking there briefly on a local Redis call is safe and simpler than making the whole tick path
 * reactive for one read. {@link #save} and {@link #delete} stay reactive because their caller, the
 * zone management API, already is.
 */
@Component
public class RedisZoneRepository implements ZoneRepository {

    public static final String KEY = "customer:zones";

    private static final Logger log = LoggerFactory.getLogger(RedisZoneRepository.class);
    private static final long LOG_EVERY = 100;

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json;
    private final CustomerProperties seedProperties;
    private final AtomicLong readFailures = new AtomicLong();

    public RedisZoneRepository(ReactiveStringRedisTemplate redis, ObjectMapper json,
            CustomerProperties seedProperties) {
        this.redis = redis;
        this.json = json;
        this.seedProperties = seedProperties;
    }

    /**
     * Seeds Redis from configuration if this is the first boot ever against it. Runs once, at
     * startup, so it can safely block -- there is no tick loop running yet to delay.
     *
     * <p>
     * Failure is logged and swallowed rather than thrown: an exception escaping an
     * {@code ApplicationReadyEvent} listener fails the whole application's startup, and an
     * unreachable Redis at boot should not be fatal here any more than it is anywhere else in this
     * service -- {@link #findAll} already degrades to an empty fleet for the same reason. A tick
     * that finds no zones logs a warning and reports zero demand rather than the service refusing
     * to start.
     */
    @EventListener(ApplicationReadyEvent.class)
    void seedIfEmpty() {
        try {
            Long size = redis.opsForHash().size(KEY).block();
            if (size != null && size > 0) {
                return;
            }

            List<Zone> defaults = seedProperties.zones().stream()
                    .map(z -> new Zone(z.id(), z.name()))
                    .toList();

            if (defaults.isEmpty()) {
                return;
            }

            Map<String, String> fields = defaults.stream()
                    .collect(Collectors.toMap(Zone::zoneId, this::toJson));

            redis.opsForHash().putAll(KEY, fields).block();
            log.info("Seeded {} zones from configuration (Redis had none)", defaults.size());
        } catch (RuntimeException e) {
            log.error("Could not seed zones into Redis; starting with an empty fleet until "
                    + "Redis is reachable and a zone is added through the API", e);
        }
    }

    @Override
    public List<Zone> findAll() {
        try {
            List<String> values = redis.<String, String>opsForHash().values(KEY).collectList().block();
            return values == null ? List.of() : values.stream().map(this::fromJson).toList();
        } catch (RuntimeException e) {
            long count = readFailures.incrementAndGet();
            if (count == 1 || count % LOG_EVERY == 0) {
                log.error("Failed to read zones from Redis ({} failures so far); this tick sees none", count, e);
            }
            return List.of();
        }
    }

    @Override
    public Mono<Void> save(Zone zone) {
        return redis.opsForHash().put(KEY, zone.zoneId(), toJson(zone)).then();
    }

    @Override
    public Mono<Void> delete(String zoneId) {
        return redis.opsForHash().remove(KEY, zoneId).then();
    }

    private String toJson(Zone zone) {
        return json.writeValueAsString(zone);
    }

    private Zone fromJson(String value) {
        return json.readValue(value, Zone.class);
    }
}
