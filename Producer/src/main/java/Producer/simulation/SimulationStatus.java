package Producer.simulation;

import java.time.Duration;

/**
 * Point-in-time view of the simulation loop.
 *
 * <p>
 * Plain primitives rather than anything managed, so this is handed straight to
 * the HTTP layer
 * without a separate response type to keep in step with it.
 *
 * @param running            whether the background loop is currently scheduled
 * @param currentTick        the most recently issued tick number; 0 means
 *                           nothing has ticked yet
 * @param tickInterval       interval the loop is running at, or the configured
 *                           default before a first start
 * @param frequencyDeviation deviation looped ticks are using, or the configured
 *                           default before a first start
 * @param lastEventCount     events published by the most recent tick; 0 before
 *                           the first tick, and also 0
 *                           for a tick that found no active plants
 */
public record SimulationStatus(
        boolean running,
        long currentTick,
        Duration tickInterval,
        double frequencyDeviation,
        int lastEventCount) {
}
