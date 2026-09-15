package Grid.simulation;

/**
 * Turns a supply/demand mismatch into the frequency deviation the next tick should carry --
 * automatic generation control (AGC), in miniature: a real grid's frequency sags when demand
 * outruns generation and rises when generation outruns demand, and a real grid operator (or, on an
 * interconnect small enough to matter, an automatic governor) corrects it back rather than leaving
 * it to drift.
 *
 * <p>
 * Deliberately free of Spring: a pure function of two numbers is what keeps this testable with
 * plain values and no context, exactly like {@code Producer.simulation.SimulationClock}.
 *
 * <h2>The formula</h2>
 *
 * The mismatch is expressed as a fraction of demand, not a raw kW figure, so one {@code gainHz}
 * setting works whether the fleet is serving three demo zones or a hundred: a 10% surplus means
 * the same thing regardless of scale. That fraction is scaled by {@code gainHz} to get a deviation
 * in Hz, matching the sign {@code GridTickEvent} already documents -- negative when demand outruns
 * supply -- and then clamped to {@code maxDeviationHz} the same way a real interconnect's
 * protection would trip before frequency ever wandered further than a system could recover from.
 *
 * <h2>Why this lags by a tick</h2>
 *
 * The totals it is given reflect whatever Producer and Distributor most recently published, which
 * is normally the <em>previous</em> tick's figures: Producer, Customer and Distributor all react to
 * {@code grid.tick} independently and in parallel, so there is no ordering guarantee that puts this
 * tick's own output and demand in front of Grid before Grid computes this tick's deviation. That
 * one-tick lag is how real AGC loops behave too -- correcting the last known error, not a
 * clairvoyant one -- and at a five-second tick it is not visible in practice.
 */
public class FrequencyController {

    private final double gainHz;
    private final double maxDeviationHz;

    public FrequencyController(double gainHz, double maxDeviationHz) {
        this.gainHz = gainHz;
        this.maxDeviationHz = maxDeviationHz;
    }

    /**
     * @param totalSupplyKw fleet-wide generation currently known, in kW
     * @param totalDemandKw system-wide demand currently known, in kW
     * @return the deviation the next tick should carry, in Hz, clamped to
     *         {@code [-maxDeviationHz, +maxDeviationHz]}; 0.0 if no demand is known yet, since a
     *         mismatch ratio against zero demand is not a meaningful signal
     */
    public double compute(double totalSupplyKw, double totalDemandKw) {
        if (totalDemandKw <= 0) {
            return 0.0;
        }

        double mismatchRatio = (totalSupplyKw - totalDemandKw) / totalDemandKw;
        double rawDeviation = mismatchRatio * gainHz;

        return Math.max(-maxDeviationHz, Math.min(maxDeviationHz, rawDeviation));
    }
}
