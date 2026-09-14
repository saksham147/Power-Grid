package Producer.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import Producer.simulation.SimulationRunner;
import Producer.simulation.SimulationStatus;

/**
 * Contract of the simulation endpoint.
 *
 * <p>
 * A slice with the runner mocked, so this asserts the HTTP contract alone and runs with no
 * Postgres, no Kafka and no Grid.
 */
@WebMvcTest(SimulationController.class)
class SimulationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationRunner runner;

    private static SimulationStatus sampleStatus() {
        return new SimulationStatus(144L, "12:00", 0L, 5L, -0.1, 3, 545.1, 1250.5);
    }

    @Test
    void statusReportsTheClockAndTheFleetTotals() throws Exception {
        given(runner.snapshot()).willReturn(sampleStatus());

        mockMvc.perform(get("/api/simulation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickNumber").value(144))
                .andExpect(jsonPath("$.simulatedTime").value("12:00"))
                .andExpect(jsonPath("$.simulatedDay").value(0))
                .andExpect(jsonPath("$.tickIntervalSeconds").value(5))
                .andExpect(jsonPath("$.frequencyDeviation").value(-0.1))
                .andExpect(jsonPath("$.fleetOutputMw").value(545.1))
                .andExpect(jsonPath("$.fleetEnergyMwh").value(1250.5));
    }

    /** Nothing decides anything from a running flag now, so it must not be in the payload. */
    @Test
    void statusNoLongerCarriesARunningFlag() throws Exception {
        given(runner.snapshot()).willReturn(sampleStatus());

        mockMvc.perform(get("/api/simulation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").doesNotExist());
    }

    /** Steering the simulation, and reading anything but its own status, both moved to Grid. */
    @Test
    void thereIsNothingLeftToStartStopOrSteerHere() throws Exception {
        mockMvc.perform(post("/api/simulation/start")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/simulation/stop")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/simulation/frequency-deviation")).andExpect(status().isNotFound());
    }
}
