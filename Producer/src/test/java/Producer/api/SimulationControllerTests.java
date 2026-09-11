package Producer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import Producer.event.ProducerOutputEvent;
import Producer.simulation.SimulationRunner;
import Producer.simulation.SimulationStatus;
import Producer.simulation.TickResult;

/**
 * Contract of the simulation endpoints.
 *
 * <p>
 * A slice rather than a {@code @SpringBootTest}: the runner is mocked, so this
 * asserts the HTTP
 * contract alone and runs with no Postgres and no Kafka. The pre-existing
 * {@code ProducerApplicationTests} boots the whole context and does need both.
 */
@WebMvcTest(SimulationController.class)
class SimulationControllerTests {

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationRunner runner;

    @Test
    void tickWithoutABodyRunsOneTickOnTheDefaults() throws Exception {
        given(runner.tickOnce(null, null)).willReturn(
                new TickResult(1L, 0.0, WHEN, List.of(new ProducerOutputEvent(7L, 1L, 250.0, WHEN))));

        mockMvc.perform(post("/api/simulation/tick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickNumber").value(1))
                .andExpect(jsonPath("$.eventsPublished").value(1))
                .andExpect(jsonPath("$.outputs[0].producerId").value(7))
                .andExpect(jsonPath("$.outputs[0].outputMw").value(250.0));
    }

    @Test
    void tickPassesAnExplicitNumberAndDeviationThrough() throws Exception {
        given(runner.tickOnce(42L, -0.1)).willReturn(new TickResult(42L, -0.1, WHEN, List.of()));

        mockMvc.perform(post("/api/simulation/tick")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tickNumber\":42,\"frequencyDeviation\":-0.1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickNumber").value(42))
                .andExpect(jsonPath("$.frequencyDeviation").value(-0.1));

        verify(runner).tickOnce(42L, -0.1);
    }

    /** An empty fleet is a reportable outcome, not a failure. */
    @Test
    void tickAgainstAnEmptyFleetIsStillOkAndReportsZero() throws Exception {
        given(runner.tickOnce(null, null)).willReturn(new TickResult(3L, 0.0, WHEN, List.of()));

        mockMvc.perform(post("/api/simulation/tick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickNumber").value(3))
                .andExpect(jsonPath("$.eventsPublished").value(0));
    }

    @Test
    void startAcceptsTheRunAndReturns202() throws Exception {
        given(runner.start(eq(Duration.ofSeconds(2)), any()))
                .willReturn(new SimulationStatus(true, 5L, Duration.ofSeconds(2), 0.0, 3));

        mockMvc.perform(post("/api/simulation/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tickInterval\":\"PT2S\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.tickInterval").value("PT2S"));
    }

    @Test
    void startWithoutABodyUsesTheConfiguredDefaults() throws Exception {
        given(runner.start(null, null))
                .willReturn(new SimulationStatus(true, 0L, Duration.ofSeconds(5), 0.0, 0));

        mockMvc.perform(post("/api/simulation/start"))
                .andExpect(status().isAccepted());

        verify(runner).start(null, null);
    }

    /** Conflicts with current state rather than being malformed, so 409 not 400. */
    @Test
    void startWhileAlreadyRunningIsAConflict() throws Exception {
        given(runner.start(any(), any()))
                .willThrow(new IllegalStateException("Simulation is already running"));

        mockMvc.perform(post("/api/simulation/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Simulation is already running"));
    }

    @Test
    void aNonPositiveIntervalIsRejected() throws Exception {
        given(runner.start(any(), any()))
                .willThrow(new IllegalArgumentException("tickInterval must be positive, got PT0S"));

        mockMvc.perform(post("/api/simulation/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tickInterval\":\"PT0S\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /** Stopping an idle simulation reports state; it does not fail. */
    @Test
    void stopIsIdempotent() throws Exception {
        given(runner.stop()).willReturn(false);
        given(runner.snapshot())
                .willReturn(new SimulationStatus(false, 6L, Duration.ofSeconds(1), 0.0, 3));

        mockMvc.perform(post("/api/simulation/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.currentTick").value(6));

        verify(runner).stop();
    }

    @Test
    void statusReportsTheCurrentSnapshot() throws Exception {
        given(runner.snapshot())
                .willReturn(new SimulationStatus(true, 12L, Duration.ofSeconds(5), -0.05, 3));

        mockMvc.perform(get("/api/simulation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.currentTick").value(12))
                .andExpect(jsonPath("$.frequencyDeviation").value(-0.05))
                .andExpect(jsonPath("$.lastEventCount").value(3));
    }

    /** An unparseable body must not surface as a 500. */
    @Test
    void aMalformedBodyIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/simulation/tick")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tickNumber\":\"not-a-number\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
