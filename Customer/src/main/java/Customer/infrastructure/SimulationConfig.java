package Customer.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import Customer.application.DemandPublisher;
import Customer.application.DemandSimulator;
import Customer.application.DemandStateStore;
import Customer.application.ZoneRepository;
import Customer.config.CustomerProperties;
import Customer.domain.SimulationClock;

/**
 * Wires the application layer to its adapters.
 *
 * <p>
 * {@link DemandSimulator} and the domain carry no Spring annotations, so this is the one place that
 * knows both the framework and the use case.
 *
 * <p>
 * No scheduler here any more: ticks come from {@code GridTickListener} reacting to Grid's clock,
 * not from a loop this service runs itself.
 */
@Configuration
@EnableConfigurationProperties(CustomerProperties.class)
public class SimulationConfig {

    @Bean
    SimulationClock simulationClock(CustomerProperties properties) {
        return new SimulationClock(
                properties.simulation().simulatedMinutesPerTick(),
                properties.simulation().realSecondsPerSimulatedMinute());
    }

    @Bean
    DemandSimulator demandSimulator(ZoneRepository zones,
            SimulationClock clock,
            DemandPublisher publisher,
            DemandStateStore stateStore) {
        return new DemandSimulator(zones, clock, publisher, stateStore);
    }
}
