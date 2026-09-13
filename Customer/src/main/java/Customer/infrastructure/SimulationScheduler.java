package Customer.infrastructure;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import Customer.application.DemandSimulator;
import Customer.domain.SimulationClock;
import jakarta.annotation.PreDestroy;

/**
 * Drives the simulation. It starts with the application and runs until the
 * application stops --
 * there is nothing to start or stop by hand.
 *
 * <p>
 * This is the only thing that knows the simulation is scheduled at all. The use
 * case exposes
 * {@code tick()} and has no opinion about what calls it, which is what lets a
 * test drive a
 * thousand ticks in a millisecond.
 *
 * <p>
 * {@code customer.simulation.autostart} exists because a {@code @SpringBootTest}
 * publishes
 * {@code ApplicationReadyEvent} like any other run, so without it merely loading
 * the context
 * writes real demand to Redis and real records to Kafka. It defaults to on; the
 * context test turns
 * it off through {@code @SpringBootTest(properties = ...)}.
 */
@Component
@ConditionalOnProperty(name = "customer.simulation.autostart", havingValue = "true", matchIfMissing = true)
public class SimulationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SimulationScheduler.class);

    private final DemandSimulator simulator;
    private final TaskScheduler scheduler;
    private final SimulationClock clock;

    private volatile ScheduledFuture<?> loop;

    public SimulationScheduler(DemandSimulator simulator,
            @Qualifier("simulationTaskScheduler") TaskScheduler scheduler,
            SimulationClock clock) {
        this.simulator = simulator;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /**
     * {@link ApplicationReadyEvent} rather than {@code @PostConstruct}: the first
     * tick writes to
     * Redis and publishes to Kafka, so both connection factories have to be built
     * first. A
     * {@code @PostConstruct} on this bean would fire while the context is still
     * wiring.
     */
    @EventListener(ApplicationReadyEvent.class)
    synchronized void startOnBoot() {
        if (loop != null) {
            return;
        }

        Duration interval = Duration.ofSeconds(clock.realSecondsPerTick());
        this.loop = scheduler.scheduleAtFixedRate(this::runTick, interval);

        log.info("Demand simulation running: one tick every {} ({} simulated minutes per tick, "
                + "1 real second = {} simulated minute(s))",
                interval, clock.simulatedMinutesPerTick(),
                1.0 / clock.realSecondsPerSimulatedMinute());
    }

    private void runTick() {
        try {
            simulator.tick();
        } catch (Exception e) {
            // Swallowed deliberately. An exception escaping a scheduled task cancels the
            // schedule, so one bad tick would end the simulation for the life of the
            // process with nothing but this line to say so.
            log.error("Tick failed; the simulation continues", e);
        }
    }

    @PreDestroy
    synchronized void shutdown() {
        ScheduledFuture<?> current = this.loop;
        if (current == null) {
            return;
        }

        // No interrupt: an in-flight tick is already mid-publish, and cutting it off to
        // save a few milliseconds is a poor trade.
        current.cancel(false);
        this.loop = null;
        log.info("Demand simulation stopped at tick {}", simulator.currentTick());
    }
}
