package Customer.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * How one customer's demand varies over the day and the week.
 *
 * <p>
 * Each profile is a 24-point hourly shape, interpolated between hours so demand
 * moves smoothly
 * rather than stepping on the hour. A published load profile is exactly this
 * shape of data, so
 * the numbers stay readable and adjustable instead of being buried in fitted
 * sinusoids.
 *
 * <p>
 * The factors average close to 1.0 across a weekday, which is what lets
 * {@code baseKwPerCustomer}
 * be read as "average demand per customer" rather than an arbitrary scale
 * constant.
 */
public enum DemandProfile {

    /**
     * Medium through the working day, high at night. Appliances -- laundry, dishwashers, EV
     * charging, AC and heating held on a thermostat rather than a schedule -- keep the evening
     * and night hours the highest part of the curve, not just a peak that immediately falls away.
     */
    RESIDENTIAL(
            new double[] {
                    0.70, 0.62, 0.55, 0.52, 0.55, 0.65, 0.85, 1.00,
                    0.95, 0.85, 0.80, 0.80, 0.82, 0.80, 0.78, 0.80,
                    0.90, 1.15, 1.50, 1.65, 1.60, 1.40, 1.10, 0.85 },
            // People are home all day at weekends, so the whole curve lifts substantially rather
            // than just the evening peak -- weekends read as "high", not "weekday plus a bit".
            1.30),

    /** Near-nothing overnight, ramps at opening, plateaus through business hours. */
    COMMERCIAL(
            new double[] {
                    0.25, 0.22, 0.20, 0.20, 0.22, 0.30, 0.50, 0.85,
                    1.25, 1.45, 1.50, 1.50, 1.45, 1.50, 1.50, 1.45,
                    1.35, 1.10, 0.80, 0.60, 0.45, 0.35, 0.30, 0.27 },
            // Mostly closed. This is the largest weekly swing of the three.
            0.35),

    /** Continuous process load: nearly flat, with a mild day-shift lift. */
    INDUSTRIAL(
            new double[] {
                    0.92, 0.90, 0.90, 0.90, 0.92, 0.95, 1.00, 1.05,
                    1.08, 1.10, 1.10, 1.08, 1.05, 1.08, 1.10, 1.10,
                    1.08, 1.05, 1.00, 0.98, 0.96, 0.95, 0.94, 0.93 },
            // Reduced shifts, not a shutdown.
            0.80);

    private static final int HOURS = 24;

    private final double[] hourly;
    private final double weekendFactor;

    DemandProfile(double[] hourly, double weekendFactor) {
        this.hourly = hourly;
        this.weekendFactor = weekendFactor;
    }

    /**
     * Multiplier on a customer's average demand at this moment.
     *
     * @return a positive factor, typically between 0.1 and 1.7
     */
    public double factorAt(LocalTime time, DayOfWeek day) {
        int hour = time.getHour();
        double withinHour = time.getMinute() / 60.0;

        // Interpolate into the next hour, wrapping 23 -> 0 so midnight is continuous.
        double from = hourly[hour];
        double to = hourly[(hour + 1) % HOURS];
        double shape = from + (to - from) * withinHour;

        return shape * (isWeekend(day) ? weekendFactor : 1.0);
    }

    private static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
