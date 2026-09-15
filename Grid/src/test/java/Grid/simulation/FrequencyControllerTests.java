package Grid.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Pure math, no Spring context: a {@link FrequencyController} needs nothing but two doubles. */
class FrequencyControllerTests {

    @Test
    void balancedSupplyAndDemandProducesNoDeviation() {
        var controller = new FrequencyController(2.0, 0.25);

        assertThat(controller.compute(1000.0, 1000.0)).isCloseTo(0.0, within(1e-9));
    }

    /** A 10% surplus at gain 2.0 Hz should read as +0.2 Hz -- comfortably inside the clamp. */
    @Test
    void surplusSupplyProducesAPositiveDeviation() {
        var controller = new FrequencyController(2.0, 0.25);

        assertThat(controller.compute(1100.0, 1000.0)).isCloseTo(0.2, within(1e-9));
    }

    /**
     * Negative means the grid is running slow -- demand outrunning generation -- matching
     * {@code GridTickEvent}'s own documented sign convention.
     */
    @Test
    void supplyShortfallProducesANegativeDeviation() {
        var controller = new FrequencyController(2.0, 0.25);

        assertThat(controller.compute(900.0, 1000.0)).isCloseTo(-0.2, within(1e-9));
    }

    @Test
    void aLargeMismatchClampsToTheMaxDeviationRatherThanExceedingIt() {
        var controller = new FrequencyController(2.0, 0.25);

        // A 50% shortfall would raw-compute to -1.0 Hz; the clamp holds it at -0.25.
        assertThat(controller.compute(500.0, 1000.0)).isCloseTo(-0.25, within(1e-9));
        assertThat(controller.compute(2000.0, 1000.0)).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void zeroTotalDemandReturnsZeroRatherThanDividingByZero() {
        var controller = new FrequencyController(2.0, 0.25);

        assertThat(controller.compute(1000.0, 0.0)).isZero();
    }
}
