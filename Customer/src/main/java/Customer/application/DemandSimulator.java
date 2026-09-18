package Customer.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Customer.domain.ConsumerUnit;
import Customer.domain.DemandModel;
import Customer.domain.Season;
import Customer.domain.SimulationClock;
import Customer.domain.Zone;
import Customer.domain.ZoneDemand;

/**
 * The use case: given a tick number, aggregate every zone's units into that zone's demand and
 * hand the result to the output ports.
 *
 * <p>
 * Deliberately free of framework annotations. It is constructed by an infrastructure configuration,
 * which is what keeps this class testable with fakes and no broker, no cache and no container.
 *
 * <p>
 * It knows nothing about Kafka, Redis, or Grid -- only that the tick number comes from somewhere
 * outside it, zones and units come from repositories read fresh every time, and demand goes
 * somewhere to be published and somewhere to be recorded.
 */
public class DemandSimulator {

    private static final Logger log = LoggerFactory.getLogger(DemandSimulator.class);

    private final ZoneRepository zones;
    private final ConsumerUnitRepository units;
    private final SimulationClock clock;
    private final DemandPublisher publisher;
    private final DemandStateStore stateStore;

    /** The last tick this simulator ran, for {@link #currentTick}; 0 before the first. */
    private volatile long lastTick;

    public DemandSimulator(ZoneRepository zones,
            ConsumerUnitRepository units,
            SimulationClock clock,
            DemandPublisher publisher,
            DemandStateStore stateStore) {
        this.zones = zones;
        this.units = units;
        this.clock = clock;
        this.publisher = publisher;
        this.stateStore = stateStore;
    }

    /**
     * Runs one tick, for the tick number Grid issued.
     *
     * <p>
     * Cost is proportional to the number of <em>units</em>, not to any population they represent:
     * each unit is one closed-form evaluation, summed per zone.
     *
     * @return what this tick produced
     */
    public DemandSnapshot tick(long tick) {
        List<Zone> currentZones = zones.findAll();
        if (currentZones.isEmpty()) {
            log.warn("No zones configured; tick {} reports zero demand", tick);
        }

        LocalTime time = clock.timeOfDay(tick);
        DayOfWeek day = clock.dayOfWeek(tick);
        Season season = Season.of(clock.dayNumber(tick));

        Map<String, List<ConsumerUnit>> unitsByZone = units.findAll().stream()
                .collect(Collectors.groupingBy(ConsumerUnit::zoneId));

        List<ZoneDemand> demands = currentZones.stream()
                .map(zone -> {
                    List<ConsumerUnit> zoneUnits = unitsByZone.getOrDefault(zone.zoneId(), List.of());
                    double kw = zoneUnits.stream().mapToDouble(u -> DemandModel.demandKw(u, time, day, season)).sum();
                    return new ZoneDemand(zone.zoneId(), zone.name(), zoneUnits.size(), kw);
                })
                .toList();

        DemandSnapshot snapshot = DemandSnapshot.of(tick, clock.formatTimeOfDay(tick), Instant.now(), demands);
        lastTick = tick;

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

    /** Tick most recently run; 0 before the first. */
    public long currentTick() {
        return lastTick;
    }

    /**
     * Runs one output port, absorbing anything it throws.
     *
     * <p>
     * Both ports are specified never to throw, and both implementations honour that. This is
     * defence against one of them breaking that contract anyway -- a serialisation fault, an
     * unexpected null -- which must not cost the tick or stop the other port from receiving the
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
