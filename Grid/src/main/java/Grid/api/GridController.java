package Grid.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Grid.api.dto.FrequencyDeviationRequest;
import Grid.simulation.GridClockRunner;
import Grid.simulation.GridStatus;
import jakarta.validation.Valid;

/**
 * Reads and steers the clock.
 *
 * <p>
 * There is no start or stop. The loop runs from application boot at a fixed pace, so the only thing
 * a caller can change is the grid condition every subsequent tick carries.
 */
@RestController
@RequestMapping("/api/grid")
public class GridController {

    private final GridClockRunner runner;

    public GridController(GridClockRunner runner) {
        this.runner = runner;
    }

    @GetMapping("/status")
    public GridStatus status() {
        return runner.snapshot();
    }

    /**
     * Sets the grid frequency deviation every subsequent tick carries.
     *
     * <p>
     * Frequency is a property of the whole grid, not of any one plant, which is why this moved here
     * from Producer once Grid existed to own it: negative means the grid is running slow, so
     * Producer's thermal governor opens up and its output rises in response.
     */
    @PutMapping("/frequency-deviation")
    public GridStatus setFrequencyDeviation(@Valid @RequestBody FrequencyDeviationRequest request) {
        return runner.setFrequencyDeviation(request.frequencyDeviation());
    }
}
