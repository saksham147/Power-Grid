package Grid.simulation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the clock, bound from {@code grid.simulation.*}.
 *
 * @param frequencyDeviation   starting grid frequency deviation in Hz. Only the starting value when
 *                             {@code autoControl} is on -- every tick after the first overwrites it
 *                             with {@link FrequencyController}'s own computation. Always changeable
 *                             at runtime through {@code PUT /api/grid/frequency-deviation}, which
 *                             also switches {@code autoControl} off; see {@code GridClockRunner}.
 * @param autostart            start ticking when the application is ready, and start the
 *                             {@code producer.output} / {@code distributor.zone-balance} listeners
 *                             with it. A {@code @SpringBootTest} publishes
 *                             {@code ApplicationReadyEvent} exactly like a real run, so the context
 *                             test turns this off -- otherwise loading the context would tick and
 *                             consume against real Redis and Kafka.
 * @param autoControl          whether Grid computes its own frequency deviation from Producer's and
 *                             Distributor's published figures each tick, rather than only ever
 *                             carrying whatever a human last set through the API -- "manage the
 *                             system automatically" is the whole point of this service existing
 *                             once Producer's thermal governor already reacts to the deviation it
 *                             carries.
 * @param autoControlGain      Hz of deviation per 100% of demand the fleet is mismatched by; see
 *                             {@link FrequencyController} for the formula
 * @param autoControlMaxDeviation clamp on the computed deviation, in Hz. Matches
 *                             {@code FrequencyDeviationRequest}'s own manual bound, so automatic and
 *                             manual control can never disagree about how far this grid model is
 *                             willing to drift from nominal.
 */
@ConfigurationProperties("grid.simulation")
public record GridProperties(
        @DefaultValue("0.0") double frequencyDeviation,
        @DefaultValue("true") boolean autostart,
        @DefaultValue("true") boolean autoControl,
        @DefaultValue("2.0") double autoControlGain,
        @DefaultValue("0.25") double autoControlMaxDeviation) {
}
