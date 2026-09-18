package Billing.billing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlantPricingTests {

    @Test
    void smallPlantsAreChargedTheFlatFloor() {
        assertThat(PlantPricing.cost(PlantType.THERMAL, 2.0)).isEqualTo(5000.0);
        assertThat(PlantPricing.cost(PlantType.WIND, 2.0)).isEqualTo(3000.0);
        assertThat(PlantPricing.cost(PlantType.SOLAR, 2.0)).isEqualTo(2000.0);
    }

    @Test
    void costScalesWithCapacityAtThisSimulationsRealPlantSizes() {
        // Thermal, Wind and Solar plants here run up to roughly 900, 150 and 200 MW respectively
        // (see Producer's default seeded fleet) -- the rates must stay sane at that scale, not
        // just at the toy single-digit-MW values a naive per-MW rate would only be tested against.
        // THERMAL 900MW: 2000 + 300*8 + 300*10 + 300*14 = 2000 + 2400 + 3000 + 4200 = 11600.
        assertThat(PlantPricing.cost(PlantType.THERMAL, 900.0)).isEqualTo(11600.0);
        // WIND 150MW: 1000 + 50*16 + 50*20 + 50*26 = 1000 + 800 + 1000 + 1300 = 4100.
        assertThat(PlantPricing.cost(PlantType.WIND, 150.0)).isEqualTo(4100.0);
        // SOLAR 200MW: 500 + 50*8 + 100*10 + 50*13 = 500 + 400 + 1000 + 650 = 2550.
        assertThat(PlantPricing.cost(PlantType.SOLAR, 200.0)).isEqualTo(2550.0);
    }

    @Test
    void theMarginalMegawattCostsMoreOnceAHigherBandIsReached() {
        // Crossing THERMAL's 600MW band boundary (both sides well above the 5000 floor, so the
        // comparison isn't masked by it): the 601st MW costs the band-3 rate (14), not the band-2
        // rate (10) the 600th MW cost -- a progressive, not flat, per-MW price.
        double at599to600 = PlantPricing.cost(PlantType.THERMAL, 600.0) - PlantPricing.cost(PlantType.THERMAL, 599.0);
        double at600to601 = PlantPricing.cost(PlantType.THERMAL, 601.0) - PlantPricing.cost(PlantType.THERMAL, 600.0);

        assertThat(at599to600).isEqualTo(10.0);
        assertThat(at600to601).isEqualTo(14.0);
    }
}
