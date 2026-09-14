package Customer.application;

import reactor.core.publisher.Mono;

/**
 * Outbound port: records the latest demand state for anything that wants to read
 * it.
 *
 * <p>
 * Latest state only. History belongs to the event stream, not to a cache.
 *
 * <p>
 * {@link #save} is the contract shared with {@link DemandPublisher}: one call per
 * tick, never throwing, never blocking. {@link #current} is a separate concern --
 * reading back what a tick already saved, for a caller that can afford to wait on it
 * (a request thread, not the tick loop) -- so it stays a {@code default} rather than
 * joining {@code save} as a second thing every implementation and test fake must
 * provide.
 */
public interface DemandStateStore {

    void save(DemandSnapshot snapshot);

    /** The latest saved snapshot, or empty if nothing has been saved yet. */
    default Mono<CurrentDemand> current() {
        return Mono.empty();
    }
}
