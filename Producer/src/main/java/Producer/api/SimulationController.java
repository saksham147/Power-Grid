package Producer.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Producer.api.dto.FrequencyDeviationRequest;
import Producer.simulation.SimulationRunner;
import Producer.simulation.SimulationStatus;
import jakarta.validation.Valid;

/**
 * Reads and steers the simulation.
 *
 * <p>
 * There is no start or stop. The loop runs from application boot at a fixed
 * pace, so the only
 * thing a caller can change is the grid condition the plants are responding to.
 */
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationRunner runner;

    public SimulationController(SimulationRunner runner) {
        this.runner = runner;
    }

    @GetMapping("/status")
    public SimulationStatus status() {
        return runner.snapshot();
    }

    /**
     * Sets the grid frequency deviation every subsequent tick runs against.
     *
     * <p>
     * The one input any plant reacts to, and only the thermal unit does: negative
     * means the grid
     * is running slow, so its governor opens up and output rises.
     */
    @PutMapping("/frequency-deviation")
    public SimulationStatus setFrequencyDeviation(@Valid @RequestBody FrequencyDeviationRequest request) {
        return runner.setFrequencyDeviation(request.frequencyDeviation());
    }
}
