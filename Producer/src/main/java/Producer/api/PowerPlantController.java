package Producer.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import Producer.api.dto.CreatePlantRequest;
import Producer.api.dto.PowerPlantResponse;
import Producer.api.dto.UpdatePlantActiveRequest;
import Producer.api.dto.UpgradePlantRequest;
import Producer.model.PowerPlant;
import Producer.model.PowerPlantRepository;
import jakarta.validation.Valid;

/**
 * The plant fleet: what exists, and what takes part in a tick.
 *
 * <p>
 * Re-rating goes through {@link PowerPlant#upgrade}, not through setters. The
 * entity still exposes
 * no individual rating setter, so it cannot be edited into a state its own
 * generation strategy
 * contradicts -- an upgrade either applies as a consistent whole or is rejected.
 */
@RestController
@RequestMapping("/api/plants")
public class PowerPlantController {

    private final PowerPlantRepository repository;

    public PowerPlantController(PowerPlantRepository repository) {
        this.repository = repository;
    }

    /**
     * @param activeOnly restrict to plants that take part in a tick, which is what
     *                   makes a listing
     *                   comparable against a tick's event count
     */
    @GetMapping
    public List<PowerPlantResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        List<PowerPlant> plants = activeOnly ? repository.findByActiveTrue() : repository.findAll();
        return plants.stream().map(PowerPlantResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PowerPlantResponse get(@PathVariable Long id) {
        return repository.findById(id)
                .map(PowerPlantResponse::from)
                .orElseThrow(() -> new PlantNotFoundException(id));
    }

    @PostMapping
    public ResponseEntity<PowerPlantResponse> create(@Valid @RequestBody CreatePlantRequest request) {
        PowerPlant saved = repository.save(request.toEntity());

        return ResponseEntity.created(URI.create("/api/plants/" + saved.getId()))
                .body(PowerPlantResponse.from(saved));
    }

    /**
     * Takes a plant in or out of service, which is the way to watch total
     * generation move without
     * touching any plant's ratings.
     */
    @PatchMapping("/{id}/active")
    @Transactional
    public PowerPlantResponse setActive(@PathVariable Long id,
            @Valid @RequestBody UpdatePlantActiveRequest request) {
        PowerPlant plant = repository.findById(id)
                .orElseThrow(() -> new PlantNotFoundException(id));

        // Managed entity inside a transaction: Hibernate dirty-checks and flushes at
        // commit, so
        // there is no save call here, matching how GenerationService writes back output.
        plant.setActive(request.active());

        return PowerPlantResponse.from(plant);
    }

    /**
     * Re-rates a unit. PUT rather than PATCH because every field is required: a
     * partial upgrade
     * would need per-field null handling to express something a second call could
     * already say.
     *
     * @throws IllegalArgumentException if the new ratings are inconsistent, mapped
     *                                  to 400
     */
    @PutMapping("/{id}")
    @Transactional
    public PowerPlantResponse upgrade(@PathVariable Long id,
            @Valid @RequestBody UpgradePlantRequest request) {
        PowerPlant plant = repository.findById(id)
                .orElseThrow(() -> new PlantNotFoundException(id));

        plant.upgrade(request.name(), request.capacityMw(), request.minOutputMw(), request.baseOutputMw());

        return PowerPlantResponse.from(plant);
    }

    /**
     * Removes a unit permanently. Deactivating via
     * {@code PATCH /{id}/active} is the reversible
     * option; this is not.
     *
     * <p>
     * Output events already published for this plant stay on
     * {@code producer.output} -- they are
     * keyed by id, so deleting the row orphans that history rather than erasing it.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        // existsById first: deleteById is silent on a missing row, which would report a
        // successful deletion of a plant that never existed.
        if (!repository.existsById(id)) {
            throw new PlantNotFoundException(id);
        }
        repository.deleteById(id);
    }

}
