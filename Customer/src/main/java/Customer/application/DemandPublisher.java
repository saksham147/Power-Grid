package Customer.application;

/**
 * Outbound port: publishes a tick's zone demand to whatever is downstream.
 *
 * <p>
 * Takes the whole snapshot rather than one zone, so a chatty implementation is
 * not expressible
 * against this interface.
 *
 * <p>
 * Implementations must not throw and must not block. A broker outage is expected
 * to cost the
 * events, never the simulation.
 */
public interface DemandPublisher {

    void publish(DemandSnapshot snapshot);
}
