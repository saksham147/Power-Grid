package Grid.simulation;

import java.time.Instant;
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

import Grid.event.GridTickEvent;
import Grid.kafka.GridTickPublisher;
import Grid.redis.RedisClockStore;
import jakarta.annotation.PreDestroy;

/**
 * The system's clock. Starts with the application and runs until it stops -- there is no start or
 * stop to call, and nothing else in the system ticks: Producer and Customer each react to
 * {@link GridTickEvent} instead of running a scheduler of their own.
 *
 * <p>
 * The pace is fixed by {@link SimulationClock}, not configured -- see that class for why.
 *
 * <h2>Automatic frequency control</h2>
 *
 * When {@code grid.simulation.auto-control} is on (the default), the deviation each tick carries is
 * no longer just whatever a human last set: {@link #tickOnce} recomputes it from
 * {@link GridStateTracker}'s current view of Producer's and Distributor's published figures via
 * {@link FrequencyController}, the same way a real grid's frequency responds to its own
 * generation/demand balance instead of sitting wherever an operator left it. {@link
 * #setFrequencyDeviation} is a manual override that switches automatic control back off, on the
 * theory that a human taking direct command of frequency should not have that command silently
 * overwritten by the very next tick.
 */
@Service
public class GridClockRunner {

    private static final Logger log = LoggerFactory.getLogger(GridClockRunner.class);

    private final GridTickPublisher publisher;
    private final RedisClockStore clockStore;
    private final TaskScheduler scheduler;
    private final GridStateTracker state;
    private final FrequencyController frequencyController;
    private final boolean autostart;

    /**
     * The tick counter. Not persisted directly -- {@link #startOnBoot} sets it from
     * {@link RedisClockStore#lastKnownTick} once on every boot, so a restart resumes the simulated
     * day and clock instead of rewinding them to midnight.
     */
    private final AtomicLong tickCounter = new AtomicLong();

    /** Serialises a tick against a deviation change arriving on a Tomcat thread. */
    private final ReentrantLock tickLock = new ReentrantLock();

    private volatile ScheduledFuture<?> loop;
    private volatile double frequencyDeviation;
    private volatile boolean autoControlEnabled;

    public GridClockRunner(GridTickPublisher publisher, RedisClockStore clockStore,
            @Qualifier("clockTaskScheduler") TaskScheduler scheduler, GridStateTracker state,
            GridProperties properties) {
        this.publisher = publisher;
        this.clockStore = clockStore;
        this.scheduler = scheduler;
        this.state = state;
        this.frequencyController = new FrequencyController(properties.autoControlGain(),
                properties.autoControlMaxDeviation());
        this.frequencyDeviation = properties.frequencyDeviation();
        this.autostart = properties.autostart();
        this.autoControlEnabled = properties.autoControl();
    }

    /**
     * Starts the loop once the application is ready.
     *
     * <p>
     * {@link ApplicationReadyEvent} rather than {@code @PostConstruct}: the first tick publishes to
     * Kafka and writes to Redis, so both connection factories have to be up first.
     */
    @EventListener(ApplicationReadyEvent.class)
    synchronized void startOnBoot() {
        if (loop != null) {
            return;
        }

        long resumeFrom = clockStore.lastKnownTick();
        tickCounter.set(resumeFrom);
        if (resumeFrom > 0) {
            log.info("Resuming clock at tick {} (day {}, {})", resumeFrom,
                    SimulationClock.dayNumber(resumeFrom), SimulationClock.formatTimeOfDay(resumeFrom));
        }

        if (!autostart) {
            log.info("Clock autostart is off; not ticking");
            return;
        }

        this.loop = scheduler.scheduleAtFixedRate(this::runTick, SimulationClock.REAL_TIME_PER_TICK);

        log.info("Clock running: one tick every {} ({} simulated minutes), {} Hz deviation",
                SimulationClock.REAL_TIME_PER_TICK, SimulationClock.SIMULATED_MINUTES_PER_TICK, frequencyDeviation);
    }

    /**
     * Manually sets the deviation every subsequent tick carries, and switches automatic control off
     * so this value sticks instead of being overwritten by the very next tick.
     *
     * <p>
     * Takes effect on the next tick; the one in flight, if any, finishes on the value it started
     * with.
     */
    public GridStatus setFrequencyDeviation(double deviation) {
        tickLock.lock();
        try {
            this.frequencyDeviation = deviation;
            this.autoControlEnabled = false;
        } finally {
            tickLock.unlock();
        }

        log.info("Frequency deviation manually set to {} Hz at tick {}; automatic control suspended",
                deviation, tickCounter.get());
        return snapshot();
    }

    public GridStatus snapshot() {
        long tick = tickCounter.get();
        return new GridStatus(
                tick,
                SimulationClock.formatTimeOfDay(tick),
                SimulationClock.dayNumber(tick),
                SimulationClock.REAL_TIME_PER_TICK.toSeconds(),
                frequencyDeviation,
                autoControlEnabled,
                state.totalSupplyKw(),
                state.totalDemandKw());
    }

    /** Visible for tests, which drive a tick directly rather than waiting on the schedule. */
    GridTickEvent tickOnce() {
        tickLock.lock();
        try {
            long number = tickCounter.incrementAndGet();

            if (autoControlEnabled) {
                frequencyDeviation = frequencyController.compute(state.totalSupplyKw(), state.totalDemandKw());
            }
            double deviation = frequencyDeviation;
            Instant now = Instant.now();

            GridTickEvent event = new GridTickEvent(number, deviation);
            publisher.publish(event);
            clockStore.save(number, deviation, now);

            return event;
        } finally {
            tickLock.unlock();
        }
    }

    private void runTick() {
        try {
            tickOnce();
        } catch (Exception e) {
            // Swallowed deliberately. An exception escaping here cancels the schedule, so a
            // single bad tick -- a Kafka hiccup -- would stop the whole system's clock with
            // nothing but this log to say so.
            log.error("Tick failed; the clock continues", e);
        }
    }

    @PreDestroy
    synchronized void shutdown() {
        ScheduledFuture<?> current = this.loop;
        if (current == null) {
            return;
        }

        current.cancel(false);
        this.loop = null;
        log.info("Clock stopped at tick {}", tickCounter.get());
    }
}
