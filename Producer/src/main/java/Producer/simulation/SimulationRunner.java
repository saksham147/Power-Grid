package Producer.simulation;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import Producer.event.GridTickEvent;
import Producer.event.ProducerOutputEvent;
import Producer.generation.GenerationService;
import Producer.history.GenerationHistoryQuery;

/**
 * Reacts to Grid's clock.
 *
 * <p>
 * {@link GridTickEvent} states that Grid is "the single owner of simulation time", and Grid now
 * exists: this class runs no scheduler of its own any more, and no longer accepts a frequency
 * deviation to apply -- both moved to Grid, which publishes a tick already carrying the deviation
 * it wants Producer's thermal governor to react to. What is left here is bookkeeping:
 * {@link #onGridTick} turns each received tick into one call to
 * {@link GenerationService#handleTick}, and {@link #snapshot} is what
 * {@code GET /api/simulation/status} reads back.
 */
@Service
public class SimulationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    private final GenerationService generationService;

    /**
     * Resumed once at construction from {@link GenerationHistoryQuery#lastKnownTick}, exactly like
     * before Grid existed -- so the status endpoint reads sensibly in the gap between boot and the
     * next tick actually arriving from Kafka, rather than showing tick 0 for up to five seconds.
     * Every tick after that overwrites it with the number Grid issued.
     */
    private volatile long currentTick;

    private volatile double frequencyDeviation;
    private volatile int lastEventCount;
    private volatile double fleetOutputMw;
    private volatile double fleetEnergyMwh;

    public SimulationRunner(GenerationService generationService, GenerationHistoryQuery history) {
        this.generationService = generationService;

        long resumeFrom = history.lastKnownTick();
        this.currentTick = resumeFrom;
        if (resumeFrom > 0) {
            log.info("Status resumes display at tick {} (day {}, {}); Grid's next tick moves it on",
                    resumeFrom, SimulationClock.dayNumber(resumeFrom), SimulationClock.formatTimeOfDay(resumeFrom));
        }
    }

    public SimulationStatus snapshot() {
        long tick = currentTick;
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

    /**
     * The one entry point Grid's tick stream drives.
     *
     * <p>
     * {@code autoStartup} keys off the same {@code producer.simulation.autostart} property the old
     * scheduler used: without it, a {@code @SpringBootTest} would start this listener against a real
     * broker the moment the context loads, exactly the problem {@code autostart} already existed to
     * prevent.
     *
     * <p>
     * A failure here is logged and swallowed rather than thrown: letting an exception escape a
     * {@code @KafkaListener} method retries the same record forever with the default error handling,
     * wedging this topic's one partition and silently stopping every tick after it. A dropped tick
     * is recoverable on its own next time Grid ticks; a wedged partition is not.
     */
    @KafkaListener(topics = "grid.tick", autoStartup = "${producer.simulation.autostart:true}")
    void onGridTick(GridTickEvent tick) {
        try {
            tickOnce(tick);
        } catch (Exception e) {
            log.error("Tick {} failed; the clock continues", tick.tickNumber(), e);
        }
    }

    /** Visible for tests, which drive a tick directly rather than through Kafka. */
    List<ProducerOutputEvent> tickOnce(GridTickEvent tick) {
        List<ProducerOutputEvent> events = generationService.handleTick(tick);

        currentTick = tick.tickNumber();
        frequencyDeviation = tick.frequencyDeviation();
        lastEventCount = events.size();
        fleetOutputMw = events.stream().mapToDouble(ProducerOutputEvent::outputMw).sum();
        // Summed here as well as on each plant, so a status read reports fleet power and
        // energy from the tick that just ran rather than from a query racing the next one.
        fleetEnergyMwh += SimulationClock.energyMwh(fleetOutputMw);

        return events;
    }
}
