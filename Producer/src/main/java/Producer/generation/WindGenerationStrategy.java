package Producer.generation;

import org.springframework.stereotype.Component;

import Producer.model.PlantType;
import Producer.model.PowerPlant;
import Producer.event.GridTickEvent;

/**
 * Simulated capacity factor for a wind farm: a slow weather front modulated by
 * fast gusts.
 *
 * <p>
 * Like solar, this ignores {@code frequencyDeviation} — a wind farm is
 * inverter-coupled and
 * takes what the wind gives it rather than following a governor.
 *
 * <p>
 * The two periods are deliberately incommensurate so the sum never repeats on a
 * short cycle, and
 * each farm is given its own phase offset: geographically separated farms do
 * not see the same
 * weather at the same moment, and a simulation where every farm peaks in unison
 * would badly
 * misrepresent how renewables aggregate.
 */
@Component
public class WindGenerationStrategy implements GenerationStrategy {

    /**
     * Slow synoptic variation — a weather system moving through over many hours.
     */
    private static final long WEATHER_FRONT_PERIOD = 2003;

    /** Fast turbulence riding on top of it. */
    private static final long GUST_PERIOD = 37;

    private static final double FRONT_WEIGHT = 0.70;
    private static final double GUST_WEIGHT = 0.20;
    private static final double NOISE_WEIGHT = 0.10;

    /**
     * Turbines idle below cut-in wind speed and are capped well short of nameplate
     * in practice.
     */
    private static final double MIN_CAPACITY_FACTOR = 0.05;
    private static final double MAX_CAPACITY_FACTOR = 0.95;

    @Override
    public PlantType getSupportedType() {
        return PlantType.WIND;
    }

    @Override
    public double calculateOutput(PowerPlant plant, GridTickEvent tick) {
        long tickNumber = tick.tickNumber();
        double phase = noise(plant.getId(), 0) * 2 * Math.PI;

        // Both shifted into [0, 1] so the weighted sum is a capacity factor, not a
        // signed swing.
        double front = 0.5 + 0.5 * Math.sin(2 * Math.PI * tickNumber / WEATHER_FRONT_PERIOD + phase);
        double gust = 0.5 + 0.5 * Math.sin(2 * Math.PI * tickNumber / GUST_PERIOD + phase);

        double capacityFactor = FRONT_WEIGHT * front
                + GUST_WEIGHT * gust
                + NOISE_WEIGHT * noise(plant.getId(), tickNumber);

        return plant.getCapacityMw()
                * Math.clamp(capacityFactor, MIN_CAPACITY_FACTOR, MAX_CAPACITY_FACTOR);
    }
}
