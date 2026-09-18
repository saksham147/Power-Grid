package Producer.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import Producer.model.PlantType;
import Producer.model.PowerPlant;

/** A forecast must reproduce exactly what the real tick will later compute -- it's the same pure
 *  strategy call, just made ahead of time -- and must refuse a type it has no basis to predict. */
class ForecastServiceTests {

    private final ForecastService forecasts = new ForecastService(
            List.of(new SolarGenerationStrategy(), new WindGenerationStrategy(), new ThermalGenerationStrategy()));

    @Test
    void aSolarForecastMatchesWhatTheStrategyWillLaterProduceForTheSameTick() {
        PowerPlant plant = new PowerPlant("Solar Farm", PlantType.SOLAR, 200, 0, 0);
        ReflectionTestUtils.setField(plant, "id", 7L);
        var strategy = new SolarGenerationStrategy();

        List<ForecastPoint> forecast = forecasts.forecast(plant, 100, 5);

        assertThat(forecast).hasSize(5);
        for (ForecastPoint point : forecast) {
            double expected = strategy.calculateOutput(plant, new Producer.event.GridTickEvent(point.tickNumber(), 0.0));
            assertThat(point.outputMw()).isEqualTo(expected);
        }
    }

    @Test
    void thermalHasNoForecastBasisAndIsRejected() {
        PowerPlant plant = new PowerPlant("Coal Unit", PlantType.THERMAL, 500, 200, 400);

        assertThatThrownBy(() -> forecasts.forecast(plant, 100, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
