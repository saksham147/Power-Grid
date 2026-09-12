package Producer.simulation;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import Producer.event.GridTickEvent;
import Producer.event.ProducerOutputEvent;
import Producer.generation.GenerationService;
import jakarta.annotation.PreDestroy;

/**
 * Drives the simulation. Starts with the application and runs until it stops --
 * there is no start
 * or stop to call.
 *
 * <p>
 * {@link GridTickEvent} states that Grid is "the single owner of simulation
 * time", and that
 * remains the intent: once Grid exists and publishes to {@code grid.tick}, that
 * is what should
 * drive this service rather than the loop here. This exists because Producer is
 * otherwise not
 * runnable on its own while Grid is still an empty skeleton.
 *
 * <p>
 * The pace is fixed by {@link SimulationClock}, not configured. See that class
 * for why.
 */
@Service
public class SimulationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    private final GenerationService generationService;
    private final TaskScheduler scheduler;

    /**
     * The simulation clock. Solar and wind derive time of day and weather phase
     * from the tick
     * number, so a counter that repeated or went backwards would not merely
     * mislabel events -- it
     * would visibly rewind the simulated world.
     */
    private final AtomicLong tickCounter = new AtomicLong();

    /**
     * Serialises a tick against a deviation change arriving on a Tomcat thread, so
     * a tick always
     * runs against one deviation rather than reading it halfway through.
     */
    private final ReentrantLock tickLock = new ReentrantLock();

    private volatile ScheduledFuture<?> loop;
    private volatile double frequencyDeviation;
    private volatile int lastEventCount;
    private volatile double fleetOutputMw;
    private volatile double fleetEnergyMwh;

    public SimulationRunner(GenerationService generationService,
            @Qualifier("simulationTaskScheduler") TaskScheduler scheduler,
            SimulationProperties properties) {
        this.generationService = generationService;
        this.scheduler = scheduler;
        this.frequencyDeviation = properties.frequencyDeviation();
    }

    /**
     * Starts the loop once the application is ready.
     *
     * <p>
     * {@link ApplicationReadyEvent} rather than {@code @PostConstruct}: the first
     * tick opens a
     * transaction and publishes to Kafka, so the datasource and the producer
     * factory both have to
     * be up. A {@code @PostConstruct} on this bean would fire while the context is
     * still wiring.
     */
    @EventListener(ApplicationReadyEvent.class)
    synchronized void startOnBoot() {
        if (loop != null) {
            return;
        }

        this.loop = scheduler.scheduleAtFixedRate(this::runTick, SimulationClock.REAL_TIME_PER_TICK);

        log.info("Simulation running: one tick every {} ({} simulated minutes), {} Hz deviation",
                SimulationClock.REAL_TIME_PER_TICK,
                SimulationClock.SIMULATED_MINUTES_PER_TICK,
                frequencyDeviation);
    }

    /**
     * Changes the deviation every subsequent tick uses. Takes effect on the next
     * tick; the one in
     * flight, if any, finishes on the value it started with.
     */
    public SimulationStatus setFrequencyDeviation(double deviation) {
        tickLock.lock();
        try {
            this.frequencyDeviation = deviation;
        } finally {
            tickLock.unlock();
        }

        log.info("Frequency deviation set to {} Hz at tick {}", deviation, tickCounter.get());
        return snapshot();
    }

    public SimulationStatus snapshot() {
        long tick = tickCounter.get();
        return new SimulationStatus(
                tick,
                SimulationClock.formatTimeOfDay(tick),
                SimulationClock.dayNumber(tick),
                SimulationClock.REAL_TIME_PER_TICK.toSeconds(),
                frequencyDeviation,
                lastEventCount,
                fleetOutputMw,
                fleetEnergyMwh);
    }

    /** Visible for tests, which drive a tick directly rather than waiting on the schedule. */
    TickResult tickOnce() {
        tickLock.lock();
        try {
            long number = tickCounter.incrementAndGet();
            double deviation = frequencyDeviation;

            List<ProducerOutputEvent> events = generationService.handleTick(new GridTickEvent(number, deviation));

            lastEventCount = events.size();
            fleetOutputMw = events.stream().mapToDouble(ProducerOutputEvent::outputMw).sum();
            // Summed here as well as on each plant, so a status read reports fleet power and
            // energy from the tick that just ran rather than from a query racing the next one.
            fleetEnergyMwh += SimulationClock.energyMwh(fleetOutputMw);

            return new TickResult(number, deviation, Instant.now(), events);
        } finally {
            tickLock.unlock();
        }
    }

    private void runTick() {
        try {
            tickOnce();
        } catch (Exception e) {
            // Swallowed deliberately. An exception escaping here cancels the schedule, so a
            // single bad tick -- a dropped database connection, a Kafka hiccup -- would end
            // the simulation for the life of the process with nothing but this log to say so.
            log.error("Tick failed; the simulation continues", e);
        }
    }

    @PreDestroy
    synchronized void shutdown() {
        ScheduledFuture<?> current = this.loop;
        if (current == null) {
            return;
        }

        // No interrupt: a tick in flight is inside a transaction, and cutting it off
        // mid-write to save a few seconds is a poor trade.
        current.cancel(false);
        this.loop = null;
        log.info("Simulation stopped at tick {}", tickCounter.get());
    }
}
