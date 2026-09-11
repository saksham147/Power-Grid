package Producer.simulation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import Producer.event.GridTickEvent;
import Producer.event.ProducerOutputEvent;
import Producer.generation.GenerationService;
import jakarta.annotation.PreDestroy;

/**
 * Drives the simulation: one tick on demand, or a background loop of them.
 *
 * <p>
 * {@link GridTickEvent} states that Grid is "the single owner of simulation
 * time", and that
 * remains the intent -- once Grid exists and publishes to {@code grid.tick},
 * that is what should be
 * driving this service, not the loop here. This exists because Producer is
 * otherwise not runnable
 * on its own: nothing in the service calls
 * {@link GenerationService#handleTick} today, so the
 * generation core cannot be exercised at all while Grid is still an empty
 * skeleton.
 *
 * <p>
 * All simulation state lives here rather than in a controller, so the HTTP layer
 * stays a
 * translation of requests into calls on this class.
 */
@Service
public class SimulationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    private final GenerationService generationService;
    private final TaskScheduler scheduler;
    private final SimulationProperties properties;

    /**
     * Shared by manual and looped ticks alike. The solar and wind strategies derive
     * time of day and
     * weather phase from the tick number, so a counter that repeated or went
     * backwards would not
     * merely mislabel events -- it would visibly rewind the simulated world.
     */
    private final AtomicLong tickCounter = new AtomicLong();

    /**
     * Serialises tick execution. The scheduler's single thread already prevents two
     * looped ticks
     * overlapping; this is what stops a manual tick arriving on a Tomcat thread
     * mid-loop and having
     * two transactions write back {@code currentOutputMw} on the same plants at
     * once.
     */
    private final ReentrantLock tickLock = new ReentrantLock();

    /** Non-null only while the loop is scheduled. Guarded by {@code this}. */
    private volatile ScheduledFuture<?> loop;

    private volatile Duration loopInterval;
    private volatile double loopFrequencyDeviation;
    private volatile int lastEventCount;

    public SimulationRunner(GenerationService generationService,
            @Qualifier("simulationTaskScheduler") TaskScheduler scheduler,
            SimulationProperties properties) {
        this.generationService = generationService;
        this.scheduler = scheduler;
        this.properties = properties;

        // Seeded so a status read before the first start still reports what a start
        // would use,
        // rather than a zeroed-out interval that was never anybody's setting.
        this.loopInterval = properties.tickInterval();
        this.loopFrequencyDeviation = properties.frequencyDeviation();

        log.info("Simulation defaults: every {} at {} Hz deviation",
                properties.tickInterval(), properties.frequencyDeviation());
    }

    /**
     * Runs exactly one tick and returns what it published.
     *
     * @param tickNumber         explicit tick number, or null to take the next one
     *                           from the shared clock.
     *                           Supplying one lets a caller replay a specific
     *                           moment, which is
     *                           reproducible because the strategies derive their
     *                           noise from it.
     * @param frequencyDeviation deviation in Hz, or null for the configured default
     */
    public TickResult tickOnce(Long tickNumber, Double frequencyDeviation) {
        double deviation = frequencyDeviation != null ? frequencyDeviation : properties.frequencyDeviation();

        tickLock.lock();
        try {
            long number;
            if (tickNumber != null) {
                number = tickNumber;
                // An explicit number must not let the next auto-numbered tick reissue one
                // already used, so the shared clock keeps up rather than being overwritten
                // (a replay of an old tick leaves the clock where it was).
                tickCounter.updateAndGet(current -> Math.max(current, tickNumber));
            } else {
                number = tickCounter.incrementAndGet();
            }

            List<ProducerOutputEvent> events = generationService.handleTick(new GridTickEvent(number, deviation));
            lastEventCount = events.size();
            return new TickResult(number, deviation, Instant.now(), events);
        } finally {
            tickLock.unlock();
        }
    }

    /**
     * Starts the background loop.
     *
     * @param interval  gap between ticks, or null for the configured default
     * @param deviation frequency deviation for every looped tick, or null for the
     *                  configured default
     * @throws IllegalStateException    if the loop is already running -- silently
     *                                  replacing it would strand the
     *                                  old schedule and make the request's effect
     *                                  depend on unseen state
     * @throws IllegalArgumentException if the interval is zero or negative
     */
    public synchronized SimulationStatus start(Duration interval, Double deviation) {
        if (isRunning()) {
            throw new IllegalStateException("Simulation is already running; stop it before starting a new run");
        }

        Duration effectiveInterval = interval != null ? interval : properties.tickInterval();
        if (effectiveInterval.isZero() || effectiveInterval.isNegative()) {
            throw new IllegalArgumentException("tickInterval must be positive, got " + effectiveInterval);
        }

        this.loopInterval = effectiveInterval;
        this.loopFrequencyDeviation = deviation != null ? deviation : properties.frequencyDeviation();
        this.loop = scheduler.scheduleAtFixedRate(this::runLoopedTick, effectiveInterval);

        log.info("Simulation started: every {} at {} Hz deviation, resuming from tick {}",
                effectiveInterval, loopFrequencyDeviation, tickCounter.get());

        return snapshot();
    }

    /**
     * Stops the loop if it is running. Idempotent: stopping an idle simulation is
     * not an error.
     *
     * @return true if a running loop was cancelled, false if there was nothing to
     *         stop
     */
    public synchronized boolean stop() {
        ScheduledFuture<?> current = this.loop;
        if (current == null) {
            return false;
        }

        // No interrupt: a tick already in flight is inside a transaction, and cutting it
        // off
        // mid-write to save a few seconds is a poor trade. It holds the lock, finishes,
        // and no
        // further tick is scheduled after it.
        current.cancel(false);
        this.loop = null;

        log.info("Simulation stopped at tick {}", tickCounter.get());
        return true;
    }

    public boolean isRunning() {
        ScheduledFuture<?> current = this.loop;
        return current != null && !current.isCancelled() && !current.isDone();
    }

    public SimulationStatus snapshot() {
        return new SimulationStatus(
                isRunning(),
                tickCounter.get(),
                loopInterval,
                loopFrequencyDeviation,
                lastEventCount);
    }

    private void runLoopedTick() {
        try {
            tickOnce(null, loopFrequencyDeviation);
        } catch (Exception e) {
            // Swallowed deliberately. An exception escaping here cancels the schedule, so a
            // single
            // bad tick -- a dropped database connection, a Kafka hiccup -- would silently
            // end a run
            // that the status endpoint would still have to be asked about to notice.
            log.error("Tick failed; the simulation loop continues", e);
        }
    }

    @PreDestroy
    void shutdown() {
        if (stop()) {
            log.info("Simulation loop cancelled during shutdown");
        }
    }
}
