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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import Producer.api.dto.CreatePlantRequest;
import Producer.api.dto.PowerPlantResponse;
import Producer.api.dto.UpdatePlantActiveRequest;
import Producer.model.PlantType;
import Producer.model.PowerPlant;
import Producer.model.PowerPlantRepository;
import jakarta.validation.Valid;

/**
 * The plant fleet: what exists, and what takes part in a tick.
 *
 * <p>
 * There is no full-update endpoint. {@link PowerPlant} exposes setters only for
 * {@code currentOutputMw} and {@code active} -- ratings are constructor-only, so
 * the entity cannot
 * be edited into a state its own generation strategy contradicts. Adding setters
 * to enable a PUT
 * would give that up for very little.
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
     * Creates a demo fleet in one call, so the simulation has something to generate
     * without hand
     * writing three POSTs.
     *
     * <p>
     * Refuses by default when the table is not empty: seeding on top of an existing
     * fleet
     * duplicates it, and a tick would then report double the events with no
     * indication why.
     *
     * @param force seed regardless of what is already there
     * @throws IllegalStateException if plants already exist and force is not set,
     *                               mapped to 409
     */
    @PostMapping("/seed")
    public ResponseEntity<List<PowerPlantResponse>> seed(@RequestParam(defaultValue = "false") boolean force) {
        long existing = repository.count();
        if (existing > 0 && !force) {
            throw new IllegalStateException(
                    "Fleet already has " + existing + " plant(s); pass ?force=true to seed on top of them");
        }

        List<PowerPlantResponse> seeded = repository.saveAll(demoFleet()).stream()
                .map(PowerPlantResponse::from)
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(seeded);
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
     * One plant of each type, so every strategy is exercised.
     *
     * <p>
     * The renewables carry a zero setpoint and zero minimum deliberately. Solar and
     * wind are
     * non-dispatchable: both strategies compute purely from {@code capacityMw} and
     * never read
     * {@code baseOutputMw}, so giving them a setpoint would record an instruction
     * nothing obeys.
     * Only the thermal unit has a real dispatch setpoint for droop to adjust
     * around.
     */
    private static List<PowerPlant> demoFleet() {
        return List.of(
                new PowerPlant("Demo Thermal Unit", PlantType.THERMAL, 500.0, 200.0, 350.0),
                new PowerPlant("Demo Solar Park", PlantType.SOLAR, 200.0, 0.0, 0.0),
                new PowerPlant("Demo Wind Farm", PlantType.WIND, 150.0, 0.0, 0.0));
    }
}
