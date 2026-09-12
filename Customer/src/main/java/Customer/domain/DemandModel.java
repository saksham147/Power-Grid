package Customer.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Computes a zone's total demand for one tick, in closed form.
 *
 * <h2>Why no customer is ever instantiated</h2>
 *
 * Each customer's demand is a random variable with mean {@code m} and standard
 * deviation
 * {@code s}. Only the zone total is ever published, and the sum of {@code N}
 * independent such
 * variables is, by the Central Limit Theorem, itself normal:
 *
 * <pre>
 *     sum  ~  Normal( N*m,  s*sqrt(N) )
 * </pre>
 *
 * So one Gaussian draw per zone reproduces what N draws would have produced. A
 * zone of two
 * million customers costs exactly what a zone of twenty costs, and the model is
 * still expressed
 * per customer -- mean, spread and profile are all per-customer quantities.
 *
 * <h2>Why there are two kinds of randomness</h2>
 *
 * The consequence of that formula is that independent noise <em>vanishes</em> at
 * scale: its
 * share of the total falls as {@code 1/sqrt(N)}, so a 250,000-customer zone
 * varies by well under
 * a tenth of a percent. That is real -- large populations genuinely aggregate to
 * smooth demand --
 * but on its own it would make a big zone a flat line, which is neither
 * realistic nor a useful
 * simulation.
 *
 * <p>
 * Real demand moves because customers are <em>not</em> independent: weather,
 * daylight and shared
 * events push a whole zone the same way at once. That is modelled by
 * {@link Zone#zoneVariability},
 * applied to the mean itself so it does not shrink with population. The
 * independent term is kept
 * as well, so small zones still behave like small samples.
 */
public final class DemandModel {

    /**
     * Period of the slow correlated drift, in ticks. Coprime with a 288-tick day, so
     * the weather
     * does not resynchronise with the daily profile and repeat.
     */
    private static final long WEATHER_PERIOD_TICKS = 577;

    /** How much of the correlated term is slow drift rather than short-lived swing. */
    private static final double DRIFT_WEIGHT = 0.7;
    private static final double SWING_WEIGHT = 1.0 - DRIFT_WEIGHT;

    private DemandModel() {
    }

    /**
     * Total demand for a zone at one tick, in kW.
     *
     * <p>
     * Deterministic: the same zone and tick always produce the same figure, so a run
     * can be
     * replayed exactly. {@code Math.random()} could not give that.
     *
     * @return demand in kW, never negative
     */
    public static double demandKw(Zone zone, LocalTime time, DayOfWeek day, long tick) {
        double perCustomerMean = zone.baseKwPerCustomer() * zone.profile().factorAt(time, day);
        long n = zone.customers();

        // Correlated: scales the mean, so it survives any population size.
        double correlated = 1.0 + zone.zoneVariability() * commonMode(zone.zoneId(), tick);
        double mean = n * perCustomerMean * correlated;

        // Independent: the CLT term, which fades as the population grows.
        double spread = zone.customerVariability() * perCustomerMean * Math.sqrt(n);
        double demand = mean + spread * gaussian(zone.zoneId().hashCode(), tick);

        // A zone cannot consume negative power. Only reachable for a tiny population
        // with a large customerVariability, but cheaper to clamp than to reason about.
        return Math.max(0.0, demand);
    }

    /**
     * Zone-wide condition in roughly [-1, 1]: a slow weather drift with a smaller
     * short-lived
     * swing on top, so demand wanders rather than jittering.
     */
    private static double commonMode(String zoneId, long tick) {
        long stream = zoneId.hashCode();
        double phase = noise(stream, 0) * 2 * Math.PI;

        double drift = Math.sin(2 * Math.PI * tick / WEATHER_PERIOD_TICKS + phase);
        double swing = gaussian(stream, tick) * 0.5;

        return DRIFT_WEIGHT * drift + SWING_WEIGHT * Math.clamp(swing, -1.0, 1.0);
    }

    /**
     * Standard normal sample from two uniforms, by Box-Muller.
     *
     * <p>
     * Clamped to six sigma: the transform is unbounded, and one freak draw would
     * otherwise put a
     * visible spike into a signal that is meant to be continuous.
     */
    private static double gaussian(long stream, long step) {
        double u1 = Math.max(noise(stream, step), Double.MIN_NORMAL);
        double u2 = noise(stream ^ 0x5DEECE66DL, step);

        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
        return Math.clamp(z, -6.0, 6.0);
    }

    /**
     * Deterministic pseudo-random value in {@code [0, 1)} from a stream and a step,
     * as a SplitMix64
     * finalizer. Stateless, allocation-free, and well distributed for adjacent seeds
     * -- which
     * {@code new Random(seed).nextDouble()} is not, and adjacent seeds are exactly
     * what consecutive
     * ticks are.
     */
    private static double noise(long stream, long step) {
        long h = stream * 0xD1B54A32D192ED03L + step * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h >>> 11) * 0x1.0p-53;
    }
}
