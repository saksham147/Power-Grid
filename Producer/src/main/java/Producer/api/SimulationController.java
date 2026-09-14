package Producer.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Producer.simulation.SimulationRunner;
import Producer.simulation.SimulationStatus;

/**
 * Reads the simulation.
 *
 * <p>
 * There is no start or stop, and no way to steer it from here any more: the loop runs from
 * application boot at a fixed pace, driven by ticks Grid publishes, and the grid condition those
 * ticks carry is set on Grid, not here -- see {@code GET /api/grid/status} and
 * {@code PUT /api/grid/frequency-deviation} on the Grid service.
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
}
