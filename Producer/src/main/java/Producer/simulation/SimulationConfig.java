package Producer.simulation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import Producer.history.HistoryProperties;

/**
 * Scheduling and configuration wiring for the simulation loop.
 */
@Configuration
@EnableConfigurationProperties({ SimulationProperties.class, HistoryProperties.class })
public class SimulationConfig {

    private static final int POOL_SIZE = 1;
    private static final int SHUTDOWN_GRACE_SECONDS = 5;

    /**
     * Declared here rather than relying on {@code @EnableScheduling} and Boot's
     * task-scheduling
     * auto-configuration, so the bean exists unconditionally and this service does
     * not pick up
     * annotation-driven scheduling it has no other use for.
     *
     * <p>
     * Pool size is deliberately 1. Ticks represent simulation time and have to run
     * one after
     * another; a larger pool would let two ticks overlap and write back
     * {@code currentOutputMw} on
     * the same plants concurrently.
     */
    @Bean
    ThreadPoolTaskScheduler simulationTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("sim-");

        // Do not let a running simulation hold up shutdown indefinitely, but do give an
        // in-flight
        // tick a bounded moment to finish its transaction rather than killing it the
        // instant the
        // context closes.
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(SHUTDOWN_GRACE_SECONDS);

        return scheduler;
    }
}
