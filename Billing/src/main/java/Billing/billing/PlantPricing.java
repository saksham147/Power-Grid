package Billing.billing;

import java.util.List;
import java.util.Map;

/**
 * What a new plant costs to build, by type and capacity -- the authoritative copy of the formula.
 * The frontend computes the same number to show a live estimate before the user submits, but this
 * is the one that actually gets charged; a client-supplied amount is never trusted (see
 * {@code Billing.api.WalletController#purchasePlant}).
 *
 * <p>
 * {@code cost = max(minimum, base + progressive per-MW cost)}: base is the fixed setup cost,
 * minimum is a floor so a tiny plant is never near-free. The per-MW cost is banded rather than
 * flat -- like a tax bracket, each {@link Band} only charges its own rate on the slice of capacity
 * that falls inside it, and the rate rises band to band. This keeps a plant's marginal MW getting
 * steadily more expensive as it grows (mirroring how larger real turbines/boilers cost
 * disproportionately more), without changing the shape of a purchase or an upgrade: {@link #cost}
 * still takes just a type and a capacity, so {@code Billing.api.WalletController#upgradePlant}'s
 * {@code newCost - oldCost} delta logic needs no change at all.
 *
 * <p>
 * Bands are calibrated so this simulation's actual plant sizes -- Thermal up to ~900 MW, Wind up
 * to ~150 MW, Solar up to ~200 MW (see the default fleet seeded in Producer) -- land close to what
 * the previous flat-rate formula charged, so existing plants' economics don't jump sharply just
 * because pricing became progressive.
 */
public final class PlantPricing {

    private record Band(double uptoMw, double perMw) {
    }

    private record Rate(double base, double minimum, List<Band> bands) {
    }

    private static final Map<PlantType, Rate> RATES = Map.of(
            PlantType.THERMAL, new Rate(2000, 5000, List.of(
                    new Band(300, 8), new Band(600, 10), new Band(Double.MAX_VALUE, 14))),
            PlantType.WIND, new Rate(1000, 3000, List.of(
                    new Band(50, 16), new Band(100, 20), new Band(Double.MAX_VALUE, 26))),
            PlantType.SOLAR, new Rate(500, 2000, List.of(
                    new Band(50, 8), new Band(150, 10), new Band(Double.MAX_VALUE, 13))));

    private PlantPricing() {
    }

    public static double cost(PlantType type, double capacityMw) {
        Rate rate = RATES.get(type);

        double bandedCost = 0;
        double coveredMw = 0;
        for (Band band : rate.bands()) {
            double mwInBand = Math.max(0, Math.min(capacityMw, band.uptoMw()) - coveredMw);
            bandedCost += mwInBand * band.perMw();
            coveredMw = band.uptoMw();
            if (capacityMw <= band.uptoMw()) {
                break;
            }
        }

        return Math.max(rate.minimum(), rate.base() + bandedCost);
    }
}
