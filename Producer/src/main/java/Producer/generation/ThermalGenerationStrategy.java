package Producer.generation;

import org.springframework.stereotype.Component;

import Producer.model.PlantType;
import Producer.model.PowerPlant;
import Producer.event.GridTickEvent;

/**
 * Governor speed-droop control for a synchronous thermal unit.
 *
 * <h2>Why frequency drives output at all</h2>
 * Grid frequency is the shaft speed of every synchronous machine on the
 * interconnect, all locked
 * together electrically. There is no storage in that system: at every instant
 * generation must equal
 * demand. When demand exceeds generation, the shortfall is made up from the
 * rotational kinetic
 * energy of the spinning masses, which slows them down. So a falling frequency
 * <em>is</em> the
 * measurement of a generation deficit — instantaneous, grid-wide, and available
 * locally to every
 * unit without any communication link. That is what makes droop a decentralised
 * controller.
 *
 * <h2>The formula</h2>
 * Droop {@code R} is defined as per-unit frequency change over per-unit power
 * change:
 * 
 * <pre>
 *     R = (Δf / f₀) / (ΔP / P_rated)
 * </pre>
 * 
 * Rearranged for the power response:
 * 
 * <pre>
 *     ΔP = -(Δf / f₀) × P_rated / R
 * </pre>
 * 
 * The negative sign is the physics, not a convention: frequency below nominal
 * means a deficit, so
 * the governor must open up. Output moves <em>opposite</em> to the deviation.
 *
 * <p>
 * Sanity check at R = 4%: a -0.2 Hz dip on a 50 Hz system gives
 * {@code -(-0.2/50)/0.04 = 0.1}, i.e. +10% of rated output. A 5% droop unit
 * swings its full range
 * over a 5% (2.5 Hz) frequency change, so a tenth of a hertz buying a few
 * percent is the right
 * order of magnitude.
 *
 * <h2>Two properties worth knowing</h2>
 * Response scales with <em>rated capacity</em>, so a 500 MW unit contributes
 * five times the MW of a
 * 100 MW unit at the same droop setting. That is what makes load sharing
 * proportional and stable
 * rather than a race between units.
 *
 * <p>
 * And droop is deliberately proportional-only. It leaves a steady-state
 * frequency error on
 * purpose: if every unit ran integral action trying to pull frequency back to
 * exactly nominal, they
 * would fight and hunt against each other. Restoring nominal frequency is
 * secondary control's job
 * (AGC), which in this architecture belongs to Grid — not here.
 */
@Component
public class ThermalGenerationStrategy implements GenerationStrategy {

    private static final double NOMINAL_FREQUENCY_HZ = 50.0;

    /**
     * 4% droop — typical for a utility turbine governor (real settings run 3–5%).
     */
    private static final double DROOP = 0.04;

    @Override
    public PlantType getSupportedType() {
        return PlantType.THERMAL;
    }

    @Override
    public double calculateOutput(PowerPlant plant, GridTickEvent tick) {
        double perUnitFrequencyError = tick.frequencyDeviation() / NOMINAL_FREQUENCY_HZ;
        double responseMw = -(perUnitFrequencyError / DROOP) * plant.getCapacityMw();

        double requested = plant.getBaseOutputMw() + responseMw;

        // Physical headroom: the unit cannot exceed its rating, and cannot be turned
        // down past its
        // technical minimum without tripping offline.
        return Math.clamp(requested, plant.getMinOutputMw(), plant.getCapacityMw());
    }
}
