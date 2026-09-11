package Producer.simulation;

import java.time.Instant;
import java.util.List;

import Producer.event.ProducerOutputEvent;

/**
 * What one tick did.
 *
 * <p>
 * The tick number is carried here rather than read back off the events because
 * a tick that found
 * no active plants publishes nothing -- and that is precisely the case a caller
 * most needs
 * explaining, since it is the state the service is in before any plant has been
 * seeded. Reading it
 * from the shared clock afterwards would also race with a loop that is running
 * at the same time.
 *
 * @param tickNumber         the tick number actually issued
 * @param frequencyDeviation deviation in Hz the tick ran with
 * @param timestamp          when the tick ran
 * @param events             one event per plant that had a strategy; empty if no
 *                           plants are active
 */
public record TickResult(
        long tickNumber,
        double frequencyDeviation,
        Instant timestamp,
        List<ProducerOutputEvent> events) {
}
