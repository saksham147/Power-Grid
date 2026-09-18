package Customer.api;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import Customer.application.ConsumerUnitRepository;
import Customer.application.CurrentDemand;
import Customer.application.DemandStateStore;
import Customer.domain.ConsumerUnit;
import Customer.domain.DemandModel;
import Customer.domain.Season;
import Customer.domain.SimulationClock;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Adds, edits and removes houses, factories and commercial buildings -- the units {@code GET
 * /api/zones} groups into zones. A change here takes effect on the very next tick, the same way a
 * zone change does: {@code DemandSimulator} reads {@link ConsumerUnitRepository#findAll} fresh
 * every time.
 */
@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final ConsumerUnitRepository units;
    private final DemandStateStore store;
    private final SimulationClock clock;

    public UnitController(ConsumerUnitRepository units, DemandStateStore store, SimulationClock clock) {
        this.units = units;
        this.store = store;
        this.clock = clock;
    }

    /**
     * Every unit, each with its demand at the current simulated moment -- computed live from
     * {@link DemandModel}, not read back from anywhere, since {@code CurrentDemand} only ever
     * stores a zone's total. See {@code DemandController} for the same pattern one level up.
     */
    @GetMapping
    public Mono<List<UnitResponse>> list() {
        Mono<List<ConsumerUnit>> currentUnits = Mono.fromCallable(units::findAll).subscribeOn(Schedulers.boundedElastic());

        return Mono.zip(store.current().defaultIfEmpty(CurrentDemand.none()), currentUnits)
                .map(tuple -> toResponses(tuple.getT1(), tuple.getT2()));
    }

    @PostMapping
    public Mono<ResponseEntity<UnitResponse>> create(@Valid @RequestBody CreateUnitRequest request) {
        ConsumerUnit unit = request.toUnit();
        return units.save(unit).thenReturn(
                ResponseEntity.created(URI.create("/api/units/" + unit.unitId()))
                        .body(UnitResponse.from(unit, 0.0)));
    }

    /** An upsert, not an update-or-404 -- see {@code ZoneController.upgrade} for why. */
    @PutMapping("/{id}")
    public Mono<UnitResponse> upgrade(@PathVariable String id, @Valid @RequestBody UpgradeUnitRequest request) {
        ConsumerUnit unit = request.toUnit(id);
        return units.save(unit).thenReturn(UnitResponse.from(unit, 0.0));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return units.delete(id);
    }

    private List<UnitResponse> toResponses(CurrentDemand current, List<ConsumerUnit> units) {
        LocalTime time = clock.timeOfDay(current.tick());
        DayOfWeek day = clock.dayOfWeek(current.tick());
        Season season = Season.of(clock.dayNumber(current.tick()));

        return units.stream()
                .map(unit -> UnitResponse.from(unit, DemandModel.demandKw(unit, time, day, season)))
                .toList();
    }
}
