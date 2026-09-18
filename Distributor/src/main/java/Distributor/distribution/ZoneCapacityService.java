package Distributor.distribution;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import Distributor.event.ZoneCapacityEvent;
import Distributor.model.ZoneCapacity;
import Distributor.model.ZoneCapacityRepository;

/**
 * The use case behind zone-capacity management: create/update/delete a zone's assigned power
 * capacity, persist it, and broadcast the change -- the same "write, then independently publish"
 * shape {@link DistributionService} already uses, so a publish failure never costs the write its
 * own outcome.
 */
@Service
public class ZoneCapacityService {

    private final ZoneCapacityRepository repository;
    private final ZoneCapacityPublisher publisher;

    public ZoneCapacityService(ZoneCapacityRepository repository, ZoneCapacityPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public List<ZoneCapacity> list() {
        return repository.findAll();
    }

    public ZoneCapacity upsert(String zoneId, String zoneName, double capacityKw) {
        ZoneCapacity capacity = repository.findById(zoneId)
                .map(existing -> {
                    existing.update(zoneName, capacityKw);
                    return existing;
                })
                .orElseGet(() -> new ZoneCapacity(zoneId, zoneName, capacityKw));

        repository.save(capacity);
        publisher.publish(new ZoneCapacityEvent(zoneId, zoneName, capacityKw, Instant.now()));
        return capacity;
    }

    /** @throws ZoneCapacityNotFoundException if no capacity is assigned to this zone */
    public void delete(String zoneId) {
        ZoneCapacity capacity = repository.findById(zoneId)
                .orElseThrow(() -> new ZoneCapacityNotFoundException(zoneId));

        repository.deleteById(zoneId);
        // capacityKw null means "removed" downstream -- see Billing.event.ZoneCapacityEvent: the
        // zone reverts to uncapped, billed at the normal rate only.
        publisher.publish(new ZoneCapacityEvent(zoneId, capacity.getZoneName(), null, Instant.now()));
    }
}
