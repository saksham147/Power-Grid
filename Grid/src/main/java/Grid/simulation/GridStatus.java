package Grid.simulation;

/**
 * Point-in-time view of the clock.
 *
 * @param tickNumber          the most recently issued tick; 0 means nothing has ticked yet.
 *                            Resumes from the last tick recorded in Redis across a restart, so this
 *                            keeps climbing rather than dropping back to 0
 * @param simulatedTime       time of day that tick represents, serialised as {@code "14:35"}
 * @param simulatedDay        simulated days elapsed since this installation's first tick
 * @param tickIntervalSeconds real seconds between ticks -- fixed, and equal to the simulated
 *                            minutes each tick covers, which is what makes one real second one
 *                            simulated minute
 * @param frequencyDeviation  deviation every tick from here on carries, in Hz
 * @param autoControlEnabled  whether that deviation is Grid's own computation from Producer's and
 *                            Distributor's published figures, rather than a human-set value
 * @param totalSupplyKw       fleet-wide generation {@link Grid.simulation.GridStateTracker}
 *                            currently knows about, in kW -- what automatic control is weighing
 *                            demand against
 * @param totalDemandKw       system-wide demand {@link Grid.simulation.GridStateTracker} currently
 *                            knows about, in kW
 */
public record GridStatus(
        long tickNumber,
        String simulatedTime,
        long simulatedDay,
        long tickIntervalSeconds,
        double frequencyDeviation,
        boolean autoControlEnabled,
        double totalSupplyKw,
        double totalDemandKw) {
}
