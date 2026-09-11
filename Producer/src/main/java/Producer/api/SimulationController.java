package Producer.api;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import Producer.api.dto.StartSimulationRequest;
import Producer.api.dto.TickRequest;
import Producer.api.dto.TickResponse;
import Producer.simulation.SimulationRunner;
import Producer.simulation.SimulationStatus;
import jakarta.validation.Valid;

/**
 * Drives the generation simulation over HTTP.
 *
 * <p>
 * Every body is optional, so each endpoint can be exercised with a bare POST and
 * the configured
 * defaults. All state lives in {@link SimulationRunner}; this is only the
 * translation into calls
 * on it.
 */
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationRunner runner;

    public SimulationController(SimulationRunner runner) {
        this.runner = runner;
    }

    /**
     * Runs exactly one tick and reports what it published. Synchronous: by the time
     * this returns,
     * the events are on the topic.
     */
    @PostMapping("/tick")
    public TickResponse tick(@RequestBody(required = false) TickRequest request) {
        Long tickNumber = request != null ? request.tickNumber() : null;
        Double deviation = request != null ? request.frequencyDeviation() : null;

        return TickResponse.from(runner.tickOnce(tickNumber, deviation));
    }

    /**
     * Starts the background loop and returns immediately.
     *
     * <p>
     * 202 rather than 200: the run has been accepted and is now proceeding on its
     * own, and the
     * tick count in the returned status is already stale by the time it is read.
     *
     * @throws IllegalStateException if a run is already going, mapped to 409
     */
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SimulationStatus start(@Valid @RequestBody(required = false) StartSimulationRequest request) {
        Duration interval = request != null ? request.tickInterval() : null;
        Double deviation = request != null ? request.frequencyDeviation() : null;

        return runner.start(interval, deviation);
    }

    /**
     * Stops the loop. Idempotent -- stopping an idle simulation reports
     * {@code running: false}
     * rather than failing, so a caller does not have to check first.
     */
    @PostMapping("/stop")
    public SimulationStatus stop() {
        runner.stop();
        return runner.snapshot();
    }

    @GetMapping("/status")
    public SimulationStatus status() {
        return runner.snapshot();
    }
}
