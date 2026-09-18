package Distributor.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Distributor.distribution.GridStateTracker;

/**
 * The one thing outside this service that can ask "what does Distributor currently see" -- added
 * for the frontend's live status page, which otherwise has no way to show Distributor at all: it
 * has no controller-driven simulation of its own, only Kafka listeners and a JPA log.
 */
@RestController
@RequestMapping("/api/distribution")
public class DistributionController {

    private final GridStateTracker state;

    public DistributionController(GridStateTracker state) {
        this.state = state;
    }

    @GetMapping("/status")
    public DistributionStatusResponse status() {
        double totalSupplyKw = state.totalSupplyKw();
        double totalDemandKw = state.totalDemandKw();
        return new DistributionStatusResponse(
                totalSupplyKw, totalDemandKw, totalSupplyKw - totalDemandKw,
                state.trackedPlantCount(), state.trackedZoneCount());
    }
}
