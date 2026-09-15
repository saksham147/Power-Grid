package Grid.simulation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Scheduling and configuration wiring for the clock.
 */
@Configuration
@EnableConfigurationProperties(GridProperties.class)
public class SimulationConfig {

    private static final int POOL_SIZE = 1;
    private static final int SHUTDOWN_GRACE_SECONDS = 5;

    /**
     * Pool size 1: this is the one loop the entire system's pace derives from, so there is nothing
     * for a second thread to do and nothing it should ever be allowed to overlap with.
     */
    @Bean
    ThreadPoolTaskScheduler clockTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("clock-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(SHUTDOWN_GRACE_SECONDS);
        return scheduler;
    }
}
