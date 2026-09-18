package Customer.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Customer.application.ConsumerUnitRepository;
import Customer.application.CurrentDemand;
import Customer.application.DemandStateStore;
import Customer.application.PeakTracker;
import Customer.application.ZoneRepository;
import Customer.domain.ConsumerUnit;
import Customer.domain.SimulationClock;
import Customer.domain.Zone;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The one thing outside this service that can ask "what's demand right now."
 *
 * <p>
 * Zone name and unit count never reach the store the tick loop writes to (see
 * {@code RedisDemandStateStore}'s javadoc for why) -- {@link ZoneRepository} and
 * {@link ConsumerUnitRepository} are the source for those, joined here against whatever
 * {@link DemandStateStore#current} recovers. A zone with nothing recovered for it (no tick has
 * run yet, or it was added after the store's last write) reports zero rather than being left out
 * or failing the request.
 */
@RestController
@RequestMapping("/api/demand")
public class DemandController {

    private final DemandStateStore store;
    private final ZoneRepository zones;
    private final ConsumerUnitRepository units;
    private final SimulationClock clock;
    private final PeakTracker peaks;

    public DemandController(DemandStateStore store, ZoneRepository zones, ConsumerUnitRepository units,
            SimulationClock clock, PeakTracker peaks) {
        this.store = store;
        this.zones = zones;
        this.units = units;
        this.clock = clock;
        this.peaks = peaks;
    }

    @GetMapping
    public Mono<DemandResponse> current() {
        // ZoneRepository.findAll() and ConsumerUnitRepository.findAll() both block on Redis;
        // offloaded so neither ever runs on the event loop this controller's other, genuinely
        // reactive work shares with every other request.
        Mono<List<Zone>> currentZones = Mono.fromCallable(zones::findAll).subscribeOn(Schedulers.boundedElastic());
        Mono<List<ConsumerUnit>> currentUnits = Mono.fromCallable(units::findAll).subscribeOn(Schedulers.boundedElastic());

        return Mono.zip(store.current().defaultIfEmpty(CurrentDemand.none()), currentZones, currentUnits)
                .map(tuple -> toResponse(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private DemandResponse toResponse(CurrentDemand current, List<Zone> zones, List<ConsumerUnit> units) {
        Map<String, Long> unitCountByZone = units.stream()
                .collect(Collectors.groupingBy(ConsumerUnit::zoneId, Collectors.counting()));

        List<ZoneDemandResponse> zoneResponses = zones.stream()
                .map(zone -> new ZoneDemandResponse(
                        zone.zoneId(),
                        zone.name(),
                        unitCountByZone.getOrDefault(zone.zoneId(), 0L),
                        current.demandByZoneId().getOrDefault(zone.zoneId(), 0.0),
                        peaks.dayPeakKw(zone.zoneId()),
                        peaks.nightPeakKw(zone.zoneId())))
                .toList();

        return new DemandResponse(
                current.tick(),
                clock.formatTimeOfDay(current.tick()),
                clock.dayNumber(current.tick()),
                current.updatedAt(),
                current.totalKw(),
                zoneResponses);
    }
}
