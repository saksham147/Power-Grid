package Producer.generation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.springframework.stereotype.Service;

import Producer.event.GridTickEvent;
import Producer.model.PlantType;
import Producer.model.PowerPlant;

/**
 * Previews a SOLAR/WIND plant's future output without waiting for those ticks to actually
 * happen. Possible only because {@link GenerationStrategy#calculateOutput} for those two types is
 * a pure function of {@code (plant, tickNumber)} -- the deterministic {@link
 * GenerationStrategy#noise} means calling it for a tick that hasn't occurred yet returns exactly
 * what that tick will produce, with nothing mutated and no state read back from the database.
 *
 * <p>
 * THERMAL is excluded: its output follows {@code frequencyDeviation}, which by definition is not
 * known ahead of the tick it belongs to (it depends on the grid's demand at that moment), so
 * "forecasting" it would mean fabricating a number with no basis, unlike solar/wind whose curves
 * are weather-and-calendar-driven and genuinely known in advance.
 */
@Service
public class ForecastService {

    private final Map<PlantType, GenerationStrategy> strategiesByType;

    public ForecastService(List<GenerationStrategy> strategies) {
        this.strategiesByType = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(GenerationStrategy::getSupportedType, Function.identity()));
    }

    /**
     * @param plant    the plant to forecast -- must be SOLAR or WIND
     * @param fromTick the first tick to include
     * @param ticks    how many ticks to forecast, starting at {@code fromTick}
     * @throws IllegalArgumentException if the plant's type has no forecastable strategy (THERMAL)
     */
    public List<ForecastPoint> forecast(PowerPlant plant, long fromTick, int ticks) {
        if (plant.getType() == PlantType.THERMAL) {
            throw new IllegalArgumentException(
                    "THERMAL output follows grid frequency at the moment of the tick and cannot be forecast ahead of it");
        }

        GenerationStrategy strategy = strategiesByType.get(plant.getType());
        // Frequency deviation is irrelevant to every non-THERMAL strategy (see their own
        // javadocs), so 0.0 here changes nothing about the forecast.
        return LongStream.range(fromTick, fromTick + ticks)
                .mapToObj(tick -> new ForecastPoint(tick, strategy.calculateOutput(plant, new GridTickEvent(tick, 0.0))))
                .toList();
    }
}
