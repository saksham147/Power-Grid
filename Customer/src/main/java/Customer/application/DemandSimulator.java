package Customer.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Customer.domain.DemandModel;
import Customer.domain.SimulationClock;
import Customer.domain.Zone;
import Customer.domain.ZoneDemand;

/**
 * The use case: advance simulated time by one tick, aggregate demand per zone,
 * and hand the
 * result to the output ports.
 *
 * <p>
 * Deliberately free of framework annotations. It is constructed by an
 * infrastructure
 * configuration, which is what keeps this class testable with two fakes and no
 * broker, no cache
 * and no container.
 *
 * <p>
 * It knows nothing about Kafka or Redis, only that demand goes somewhere to be
 * published and
 * somewhere to be recorded.
 */
public class DemandSimulator {

    private static final Logger log = LoggerFactory.getLogger(DemandSimulator.class);

    private final List<Zone> zones;
    private final SimulationClock clock;
    private final DemandPublisher publisher;
    private final DemandStateStore stateStore;

    /** Simulation time. The demand model derives its randomness from it, so it only ever advances. */
    private final AtomicLong tickCounter = new AtomicLong();

    public DemandSimulator(List<Zone> zones,
            SimulationClock clock,
            DemandPublisher publisher,
            DemandStateStore stateStore) {
        this.zones = List.copyOf(zones);
        this.clock = clock;
        this.publisher = publisher;
        this.stateStore = stateStore;

        if (this.zones.isEmpty()) {
            log.warn("No zones configured; every tick will report zero demand");
        } else {
            long customers = this.zones.stream().mapToLong(Zone::customers).sum();
            log.info("Simulating {} zones covering {} customers, one tick per {}s ({} simulated minutes)",
                    this.zones.size(), customers, clock.realSecondsPerTick(), clock.simulatedMinutesPerTick());
        }
    }

    /**
     * Runs one tick.
     *
     * <p>
     * Cost is proportional to the number of <em>zones</em>, not customers: each zone
     * is one
     * closed-form evaluation regardless of how many customers it represents.
     *
     * @return what this tick produced
     */
    public DemandSnapshot tick() {
        long tick = tickCounter.incrementAndGet();
        LocalTime time = clock.timeOfDay(tick);
        DayOfWeek day = clock.dayOfWeek(tick);

        List<ZoneDemand> demands = zones.stream()
                .map(zone -> new ZoneDemand(
                        zone.zoneId(),
                        zone.name(),
                        zone.customers(),
                        DemandModel.demandKw(zone, time, day, tick)))
                .toList();

        DemandSnapshot snapshot = DemandSnapshot.of(tick, clock.formatTimeOfDay(tick), Instant.now(), demands);

        // Publish before recording state. Both ports are asynchronous, so this does not
        // order their completion -- but if the process dies between the two, a missed
        // event is a permanent gap in the stream whereas stale Redis is overwritten by
        // the next tick.
        guard("publish", () -> publisher.publish(snapshot));
        guard("state save", () -> stateStore.save(snapshot));

        log.debug("Tick {} ({} {}): {} kW across {} zones",
                tick, snapshot.simulatedTime(), day, Math.round(snapshot.totalKw()), demands.size());

        return snapshot;
    }

    /** Tick most recently issued; 0 before the first. */
    public long currentTick() {
        return tickCounter.get();
    }

    /**
     * Runs one output port, absorbing anything it throws.
     *
     * <p>
     * Both ports are specified never to throw, and both implementations honour that.
     * This is
     * defence against one of them breaking that contract anyway -- a serialisation
     * fault, an
     * unexpected null -- which must not cost the tick or stop the other port from
     * receiving the
     * same snapshot.
     */
    private static void guard(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Demand {} failed; the simulation continues", what, e);
        }
    }
}
