package Producer.event;

/**
 * Grid's clock pulse, consumed from {@code grid.tick}. Grid is the single owner
 * of simulation time;
 * every other service reacts to this and runs no scheduler of its own.
 *
 * @param tickNumber         monotonic tick counter — the simulation's only
 *                           notion of "when"
 * @param frequencyDeviation departure from nominal grid frequency, in Hz.
 *                           Negative means the grid
 *                           is running slow, i.e. demand is outrunning
 *                           generation.
 */
public record GridTickEvent(long tickNumber, double frequencyDeviation) {}
