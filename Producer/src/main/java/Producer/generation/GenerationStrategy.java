package Producer.generation;

import Producer.model.PlantType;
import Producer.model.PowerPlant;
import Producer.event.GridTickEvent;

/**
 * Computes a plant's output for one tick. Implementations are stateless
 * singletons; Spring collects
 * them into a {@code Map<PlantType, GenerationStrategy>} at startup.
 */
public interface GenerationStrategy {

    PlantType getSupportedType();

    /**
     * @param plant the unit to compute for — never mutated here; the caller owns
     *              the write-back
     * @param tick  the current tick. Carries {@code tickNumber} (the simulation
     *              clock, which the
     *              weather-driven strategies need) as well as
     *              {@code frequencyDeviation}.
     * @return output in MW for this tick
     */
    double calculateOutput(PowerPlant plant, GridTickEvent tick);

    /**
     * Deterministic pseudo-random value in {@code [0, 1)} derived from a stream and
     * a step.
     *
     * <p>
     * This is what keeps the simulation replayable: feeding the same tick sequence
     * in twice
     * produces byte-identical output, which {@code Math.random()} could not give
     * us. Used by the
     * weather-driven strategies for cloud cover and gusts; {@code THERMAL} ignores
     * it.
     *
     * <p>
     * Implemented as a SplitMix64 finalizer — stateless, allocation-free, and
     * well-distributed
     * for adjacent seeds (which {@code new Random(seed).nextDouble()} is
     * notoriously not).
     *
     * @param stream distinguishes independent series, e.g. one per plant id
     * @param step   position within the series, i.e. the tick number
     */
    default double noise(long stream, long step) {
        long h = stream * 0xD1B54A32D192ED03L + step * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h >>> 11) * 0x1.0p-53;
    }
}
