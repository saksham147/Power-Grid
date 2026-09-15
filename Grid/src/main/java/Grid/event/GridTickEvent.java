package Grid.event;

/**
 * The clock pulse, published to {@code grid.tick}. Every other service reacts to this and runs no
 * scheduler of its own.
 *
 * <p>
 * A structural, not a Java, contract: Producer and Customer each deserialize this by binding
 * directly to their own copy of this record rather than sharing this class file (there is no
 * shared library module between the services), so field names and order here are the actual
 * interface. Change either without changing the other and the JSON still round-trips; rename a
 * field here without renaming it everywhere else and it silently stops.
 *
 * @param tickNumber         monotonic tick counter -- the simulation's only notion of "when"
 * @param frequencyDeviation departure from nominal grid frequency, in Hz. Negative means the grid
 *                           is running slow, i.e. demand is outrunning generation.
 */
public record GridTickEvent(long tickNumber, double frequencyDeviation) {
}
