package Customer.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import Customer.application.DemandSimulator;

/**
 * Reacts to Grid's clock instead of this service scheduling its own ticks.
 *
 * <p>
 * {@code autoStartup} keys off {@code customer.simulation.autostart} -- the same property that used
 * to gate {@code SimulationScheduler} -- so a {@code @SpringBootTest} does not start this listener
 * against a real broker the moment the context loads.
 */
@Component
public class GridTickListener {

    private static final Logger log = LoggerFactory.getLogger(GridTickListener.class);

    private final DemandSimulator simulator;

    public GridTickListener(DemandSimulator simulator) {
        this.simulator = simulator;
    }

    /**
     * A failure here is logged and swallowed rather than thrown: letting an exception escape a
     * {@code @KafkaListener} method retries the same record forever under the default error
     * handling, wedging this topic's one partition and silently stopping every tick after it.
     */
    @KafkaListener(topics = "grid.tick", autoStartup = "${customer.simulation.autostart:true}")
    void onGridTick(GridTickEvent tick) {
        try {
            simulator.tick(tick.tickNumber());
        } catch (Exception e) {
            log.error("Tick {} failed; the simulation continues", tick.tickNumber(), e);
        }
    }
}
