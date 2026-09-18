package Customer.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Computes one unit's demand for one tick.
 *
 * <p>
 * {@code capacity x type x season}: a unit's rated capacity scaled by its type's demand shape at
 * the current time of day and day of week, then by the current {@link Season}'s demand factor.
 * Deterministic on purpose -- the same unit and the same moment always produce the same figure,
 * matching every other simulated quantity in this project (see {@code
 * Producer.simulation.SimulationClock} and its own generation strategies, which use the same
 * shape: a rating times type-and-time-specific factors).
 *
 * <p>
 * Season is kept as its own multiplicative term rather than folded into {@link DemandProfile}'s
 * own hourly shape -- "what kind of building this is" and "what time of year it is" are
 * orthogonal, and collapsing them would mean every profile's shape carrying a second, unrelated
 * axis of variation.
 *
 * <p>
 * This replaced a population/statistics model that existed only because a zone used to represent
 * an anonymous population too large to simulate customer-by-customer. A named unit with its own
 * capacity has no such population to collapse away, so there is nothing left for randomness to
 * earn its place here.
 */
public final class DemandModel {

    private DemandModel() {
    }

    /** @return demand in kW, never negative, with no seasonal adjustment (season factor 1.0) --
     *  kept for call sites without a day number to derive a season from. */
    public static double demandKw(ConsumerUnit unit, LocalTime time, DayOfWeek day) {
        return demandKw(unit, time, day, Season.SPRING);
    }

    /** @return demand in kW, never negative */
    public static double demandKw(ConsumerUnit unit, LocalTime time, DayOfWeek day, Season season) {
        return Math.max(0.0, unit.capacityKw() * unit.type().factorAt(time, day) * season.demandFactor());
    }
}
