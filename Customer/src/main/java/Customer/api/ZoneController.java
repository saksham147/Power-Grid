package Customer.api;

import java.net.URI;
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

import Customer.application.ZoneRepository;
import Customer.domain.Zone;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Adds and removes zones. Read-back of the current fleet is here too; current demand for it is
 * {@code GET /api/demand}, not this.
 *
 * <p>
 * A change here takes effect on the very next tick: {@code DemandSimulator} reads
 * {@link ZoneRepository#findAll} fresh every time, rather than freezing the fleet at startup.
 */
@RestController
@RequestMapping("/api/zones")
public class ZoneController {

    private final ZoneRepository zones;

    public ZoneController(ZoneRepository zones) {
        this.zones = zones;
    }

    @GetMapping
    public Mono<List<ZoneResponse>> list() {
        return Mono.fromCallable(zones::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .map(all -> all.stream().map(ZoneResponse::from).toList());
    }

    @PostMapping
    public Mono<ResponseEntity<ZoneResponse>> create(@Valid @RequestBody CreateZoneRequest request) {
        Zone zone = request.toZone();
        return zones.save(zone).thenReturn(
                ResponseEntity.created(URI.create("/api/zones/" + zone.zoneId()))
                        .body(ZoneResponse.from(zone)));
    }

    /**
     * Re-rates a zone: new population, profile and demand shape, applied together.
     *
     * <p>
     * An upsert, not an update-or-404: {@link ZoneRepository#save} already "adds a zone, or
     * replaces one with the same id", so a PUT against an id that does not exist yet creates it
     * rather than failing. {@code ZoneRepository} has no {@code findById} to check existence
     * against, and adding one just to reject this case would be a bigger change than a PUT's own
     * idempotent-upsert semantics call for.
     */
    @PutMapping("/{id}")
    public Mono<ZoneResponse> upgrade(@PathVariable String id, @Valid @RequestBody UpgradeZoneRequest request) {
        Zone zone = request.toZone(id);
        return zones.save(zone).thenReturn(ZoneResponse.from(zone));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return zones.delete(id);
    }
}
