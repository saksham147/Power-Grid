package Producer.simulation;

/**
 * Point-in-time view of the simulation.
 *
 * <p>
 * There is no {@code running} flag: the loop starts with the application and
 * runs until it stops,
 * so a caller has nothing to decide from it.
 *
 * @param tickNumber          the most recently issued tick; 0 means nothing has
 *                            ticked yet. Resumes from the last tick recorded in history across a
 *                            restart, so this keeps climbing rather than dropping back to 0
 * @param simulatedTime       time of day that tick represents, serialised as
 *                            {@code "14:35"}
 * @param simulatedDay        simulated days elapsed since the very first tick this installation
 *                            ever ran, not since this process started
 * @param tickIntervalSeconds real seconds between ticks -- fixed, and equal to
 *                            the simulated minutes
 *                            each tick covers, which is what makes one real
 *                            second one simulated minute
 * @param frequencyDeviation  deviation every tick is currently using, in Hz
 * @param lastEventCount      events published by the most recent tick; also 0
 *                            for a tick that found no
 *                            active plants
 * @param fleetOutputMw       what the fleet generated on that tick -- power, an
 *                            instantaneous rate
 * @param fleetEnergyMwh      energy the fleet has generated since this process started -- unlike
 *                            {@code tickNumber}, this total is kept only in memory and does not
 *                            resume across a restart. Each plant's own lifetime total, in
 *                            {@code power_plant.energy_mwh}, is what does not reset
 */
public record SimulationStatus(
        long tickNumber,
        String simulatedTime,
        long simulatedDay,
        long tickIntervalSeconds,
        double frequencyDeviation,
        int lastEventCount,
        double fleetOutputMw,
        double fleetEnergyMwh) {
}
