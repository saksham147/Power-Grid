package Distributor.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import Distributor.distribution.GridStateTracker;
import Distributor.distribution.ZoneCapacityService;
import jakarta.validation.Valid;

/**
 * Zone-capacity management: the "line zone manager" surface -- add, edit, and remove the power
 * capacity assigned to each zone. Distributor's own distribution math is unchanged by any of this
 * (see {@code DistributionService}); a capacity here is a billing ceiling, applied downstream by
 * Billing as a surcharge on demand above it, not a cap this service enforces itself.
 */
@RestController
@RequestMapping("/api/zones")
public class ZoneCapacityController {

    private final ZoneCapacityService service;
    private final GridStateTracker state;

    public ZoneCapacityController(ZoneCapacityService service, GridStateTracker state) {
        this.service = service;
        this.state = state;
    }

    @GetMapping
    public List<ZoneCapacityResponse> list() {
        return service.list().stream()
                .map(capacity -> ZoneCapacityResponse.from(capacity, state.demandKwFor(capacity.getZoneId())))
                .toList();
    }

    @PostMapping
    public ZoneCapacityResponse create(@Valid @RequestBody CreateZoneCapacityRequest request) {
        var capacity = service.upsert(request.zoneId(), request.zoneName(), request.capacityKw());
        return ZoneCapacityResponse.from(capacity, state.demandKwFor(capacity.getZoneId()));
    }

    @PutMapping("/{zoneId}")
    public ZoneCapacityResponse update(@PathVariable String zoneId, @Valid @RequestBody UpdateZoneCapacityRequest request) {
        var capacity = service.upsert(zoneId, request.zoneName(), request.capacityKw());
        return ZoneCapacityResponse.from(capacity, state.demandKwFor(capacity.getZoneId()));
    }

    @DeleteMapping("/{zoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String zoneId) {
        service.delete(zoneId);
    }
}
