package Customer.application;

import java.util.List;

import Customer.domain.ConsumerUnit;
import reactor.core.publisher.Mono;

/**
 * Outbound port: the units a tick should simulate -- the {@link ZoneRepository} of the unit world.
 *
 * <p>
 * {@link #findAll} is read fresh every tick, exactly like {@link ZoneRepository#findAll}, so a
 * unit added, edited or removed at runtime takes effect on the very next tick.
 */
public interface ConsumerUnitRepository {

    List<ConsumerUnit> findAll();

    default List<ConsumerUnit> findByZoneId(String zoneId) {
        return findAll().stream().filter(u -> u.zoneId().equals(zoneId)).toList();
    }

    /** Adds a unit, or replaces one with the same id. */
    default Mono<Void> save(ConsumerUnit unit) {
        return Mono.error(new UnsupportedOperationException());
    }

    /** Removes a unit by id. Completes normally even if no such unit existed. */
    default Mono<Void> delete(String unitId) {
        return Mono.empty();
    }

    /** Removes every unit belonging to a zone -- the cascade a zone deletion needs. */
    default Mono<Void> deleteByZoneId(String zoneId) {
        return Mono.empty();
    }
}
