package Producer.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import Producer.api.dto.CreateStorageRequest;
import Producer.api.dto.StorageResponse;
import Producer.api.dto.UpgradeStorageRequest;
import Producer.model.StorageUnit;
import Producer.model.StorageUnitRepository;
import jakarta.validation.Valid;

/**
 * The storage fleet -- mirrors {@code PowerPlantController}'s shape exactly (list/get/create/
 * upgrade/delete), since storage's lifecycle CRUD has no meaningful difference from a plant's.
 * {@link Producer.storage.StorageCycleService} is what actually charges/discharges these units
 * every tick; this controller only manages which units exist and how they're rated.
 */
@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final StorageUnitRepository repository;

    public StorageController(StorageUnitRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<StorageResponse> list() {
        return repository.findAll().stream().map(StorageResponse::from).toList();
    }

    @GetMapping("/{id}")
    public StorageResponse get(@PathVariable Long id) {
        return repository.findById(id)
                .map(StorageResponse::from)
                .orElseThrow(() -> new StorageNotFoundException(id));
    }

    @PostMapping
    public ResponseEntity<StorageResponse> create(@Valid @RequestBody CreateStorageRequest request) {
        StorageUnit saved = repository.save(request.toEntity());
        return ResponseEntity.created(URI.create("/api/storage/" + saved.getId()))
                .body(StorageResponse.from(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    public StorageResponse upgrade(@PathVariable Long id, @Valid @RequestBody UpgradeStorageRequest request) {
        StorageUnit unit = repository.findById(id).orElseThrow(() -> new StorageNotFoundException(id));
        unit.upgrade(request.name(), request.capacityKwh(), request.maxChargeRateKw(), request.maxDischargeRateKw());
        return StorageResponse.from(unit);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new StorageNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
