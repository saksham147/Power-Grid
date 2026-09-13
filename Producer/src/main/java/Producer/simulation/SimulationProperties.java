package Producer.simulation;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the simulation loop, bound from {@code producer.simulation.*}.
 *
 * <p>
 * The {@code @DefaultValue}s duplicate {@code application.yml} on purpose, so the
 * service still
 * starts sanely if the block is ever removed.
 *
 * @param frequencyDeviation starting grid frequency deviation in Hz; changeable at
 *                           runtime through
 *                           {@code PUT /api/simulation/frequency-deviation}
 * @param autostart          start ticking when the application is ready. A
 *                           {@code @SpringBootTest}
 *                           publishes {@code ApplicationReadyEvent} exactly like a
 *                           real run, so tests
 *                           turn this off or merely loading the context writes real
 *                           rows and real events.
 */
@ConfigurationProperties("producer.simulation")
public record SimulationProperties(
        @DefaultValue("0.0") double frequencyDeviation,
        @DefaultValue("true") boolean autostart) {
}
