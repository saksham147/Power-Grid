package Producer.generation;

import org.springframework.stereotype.Component;

import Producer.model.PlantType;
import Producer.model.PowerPlant;
import Producer.event.GridTickEvent;

/**
 * Simulated capacity factor for a PV farm: a diurnal curve with cloud cover on
 * top.
 *
 * <p>
 * Note that {@code frequencyDeviation} is ignored, and that is physically
 * correct. PV is
 * inverter-coupled and non-dispatchable — it has no governor and no rotating
 * mass, so it contributes
 * nothing to primary frequency response. (Real grids increasingly ask inverters
 * for synthetic
 * inertia, but that is an added control mode, not the default behaviour of a
 * plain farm.)
 *
 * <p>
 * Output is driven by {@code tickNumber} rather than wall-clock time, which
 * keeps the whole
 * simulation a pure function of the tick stream.
 */
@Component
public class SolarGenerationStrategy implements GenerationStrategy {

    /** One simulated day. At 288 ticks that is a tick every 5 simulated minutes. */
    private static final long TICKS_PER_DAY = 288;

    /** Daylight window as a fraction of the day: 06:00 to 18:00. */
    private static final double SUNRISE = 0.25;
    private static final double SUNSET = 0.75;

    /** Cloud cover scales clear-sky output down to somewhere in [0.70, 1.0]. */
    private static final double MIN_CLOUD_FACTOR = 0.70;

    @Override
    public PlantType getSupportedType() {
        return PlantType.SOLAR;
    }

    @Override
    public double calculateOutput(PowerPlant plant, GridTickEvent tick) {
        double dayFraction = Math.floorMod(tick.tickNumber(), TICKS_PER_DAY) / (double) TICKS_PER_DAY;

        // Half-sine standing in for solar elevation: zero at sunrise, peak at solar
        // noon, zero at
        // sunset. Outside the daylight window the sine goes negative, and the floor
        // clamps it to
        // night-time zero.
        double elevation = Math.sin(Math.PI * (dayFraction - SUNRISE) / (SUNSET - SUNRISE));
        double clearSky = Math.max(0.0, elevation);

        double cloudFactor = MIN_CLOUD_FACTOR
                + (1.0 - MIN_CLOUD_FACTOR) * noise(plant.getId(), tick.tickNumber());

        return plant.getCapacityMw() * clearSky * cloudFactor;
    }
}
