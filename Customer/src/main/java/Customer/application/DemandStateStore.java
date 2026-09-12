package Customer.application;

/**
 * Outbound port: records the latest demand state for anything that wants to read
 * it.
 *
 * <p>
 * Latest state only. History belongs to the event stream, not to a cache.
 *
 * <p>
 * Same contract as {@link DemandPublisher}: one call per tick, never throwing,
 * never blocking.
 */
public interface DemandStateStore {

    void save(DemandSnapshot snapshot);
}
