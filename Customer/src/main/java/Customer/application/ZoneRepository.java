package Customer.application;

import java.util.List;

import Customer.domain.Zone;
import reactor.core.publisher.Mono;

/**
 * Outbound port: the zones a tick should simulate.
 *
 * <p>
 * {@link #findAll} is the only method {@link DemandSimulator} calls, and it calls it fresh on every
 * tick rather than once at construction -- that is what lets a zone added or removed at runtime
 * take effect on the very next tick instead of requiring a restart. It stays the sole abstract
 * method so a test fake can still be a lambda, exactly as before this port existed.
 *
 * <p>
 * {@link #save} and {@link #delete} are reactive where {@link #findAll} is not: their one caller is
 * the zone management API, which already is, where {@link #findAll}'s caller is the synchronous
 * tick loop.
 */
public interface ZoneRepository {

    List<Zone> findAll();

    /** Adds a zone, or replaces one with the same id. */
    default Mono<Void> save(Zone zone) {
        return Mono.error(new UnsupportedOperationException());
    }

    /** Removes a zone by id. Completes normally even if no such zone existed. */
    default Mono<Void> delete(String zoneId) {
        return Mono.empty();
    }
}
