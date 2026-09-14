package Customer.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Customer.application.CurrentDemand;
import Customer.application.DemandStateStore;
import Customer.application.ZoneRepository;
import Customer.domain.SimulationClock;
import Customer.domain.Zone;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The one thing outside this service that can ask "what's demand right now."
 *
 * <p>
 * Zone name, customer count and profile never reach the store the tick loop writes to (see
 * {@code RedisDemandStateStore}'s javadoc for why) -- {@link ZoneRepository} is the source for
 * those, joined here against whatever {@link DemandStateStore#current} recovers. A zone with
 * nothing recovered for it (no tick has run yet, or it was added after the store's last write)
 * reports zero rather than being left out or failing the request.
 */
@RestController
@RequestMapping("/api/demand")
public class DemandController {

    private final DemandStateStore store;
    private final ZoneRepository zones;
    private final SimulationClock clock;

    public DemandController(DemandStateStore store, ZoneRepository zones, SimulationClock clock) {
        this.store = store;
        this.zones = zones;
        this.clock = clock;
    }

    @GetMapping
    public Mono<DemandResponse> current() {
        // ZoneRepository.findAll() blocks on Redis; offloaded so it never runs on the event loop
        // this controller's other, genuinely reactive work shares with every other request.
        Mono<List<Zone>> currentZones = Mono.fromCallable(zones::findAll)
                .subscribeOn(Schedulers.boundedElastic());

        return Mono.zip(store.current().defaultIfEmpty(CurrentDemand.none()), currentZones)
                .map(tuple -> toResponse(tuple.getT1(), tuple.getT2()));
    }

    private DemandResponse toResponse(CurrentDemand current, List<Zone> zones) {
        List<ZoneDemandResponse> zoneResponses = zones.stream()
                .map(zone -> new ZoneDemandResponse(
                        zone.zoneId(),
                        zone.name(),
                        zone.customers(),
                        zone.profile().name(),
                        current.demandByZoneId().getOrDefault(zone.zoneId(), 0.0)))
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
