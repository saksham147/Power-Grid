package Customer.infrastructure.kafka;

/**
 * Grid's clock pulse, consumed from {@code grid.tick}. Grid is the single owner of simulation time;
 * this service runs no scheduler of its own and reacts to this instead.
 *
 * <p>
 * A structural, not a Java, contract: this is Customer's own copy of the same fields Grid and
 * Producer each have their own copy of -- there is no shared library module between the services,
 * so the JSON shape is the actual interface, not this class.
 *
 * @param tickNumber         monotonic tick counter -- the simulation's only notion of "when"
 * @param frequencyDeviation departure from nominal grid frequency, in Hz. Unused here -- only
 *                           Producer's thermal governor reacts to it -- but received as part of the
 *                           same event rather than a second topic.
 */
public record GridTickEvent(long tickNumber, double frequencyDeviation) {
}
