package Customer.infrastructure;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import Customer.application.DemandPublisher;
import Customer.application.DemandSimulator;
import Customer.application.DemandStateStore;
import Customer.config.CustomerProperties;
import Customer.domain.SimulationClock;
import Customer.domain.Zone;

/**
 * Wires the application layer to its adapters.
 *
 * <p>
 * {@link DemandSimulator} and the domain carry no Spring annotations, so this is
 * the one place
 * that knows both the framework and the use case. Translating configuration into
 * domain objects
 * here is also what keeps {@code Zone}'s validation meaningful: a malformed zone
 * fails the context
 * at startup rather than every tick thereafter.
 */
@Configuration
@EnableConfigurationProperties(CustomerProperties.class)
public class SimulationConfig {

    private static final int POOL_SIZE = 1;
    private static final int SHUTDOWN_GRACE_SECONDS = 5;

    @Bean
    SimulationClock simulationClock(CustomerProperties properties) {
        return new SimulationClock(
                properties.simulation().simulatedMinutesPerTick(),
                properties.simulation().realSecondsPerSimulatedMinute());
    }

    @Bean
    DemandSimulator demandSimulator(CustomerProperties properties,
            SimulationClock clock,
            DemandPublisher publisher,
            DemandStateStore stateStore) {
        List<Zone> zones = properties.zones().stream()
                .map(z -> new Zone(z.id(), z.name(), z.customers(), z.profile(),
                        z.baseKwPerCustomer(), z.customerVariability(), z.zoneVariability()))
                .toList();

        return new DemandSimulator(zones, clock, publisher, stateStore);
    }

    /**
     * Pool size 1, deliberately. Ticks are simulated time and must run one after
     * another; a larger
     * pool would let two ticks interleave and publish out of order.
     */
    @Bean
    ThreadPoolTaskScheduler simulationTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("demand-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(SHUTDOWN_GRACE_SECONDS);
        return scheduler;
    }
}
