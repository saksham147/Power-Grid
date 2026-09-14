package Producer.simulation;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for reacting to Grid's clock, bound from {@code producer.simulation.*}.
 *
 * <p>
 * The {@code @DefaultValue} duplicates {@code application.yml} on purpose, so the
 * service still starts sanely if the block is ever removed.
 *
 * @param autostart whether {@code SimulationRunner}'s {@code @KafkaListener} auto-starts. A
 *                  {@code @SpringBootTest} publishes {@code ApplicationReadyEvent} exactly like a
 *                  real run, and Spring Kafka starts a listener's container at that same point, so
 *                  tests turn this off -- otherwise merely loading the context would consume real
 *                  ticks from a real broker and write real rows and real events.
 */
@ConfigurationProperties("producer.simulation")
public record SimulationProperties(
        @DefaultValue("true") boolean autostart) {
}
