package Grid.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import Grid.simulation.GridClockRunner;
import Grid.simulation.GridStatus;

/**
 * Contract of the clock endpoints: a slice with the runner mocked, so this runs with no Redis and
 * no Kafka.
 */
@WebMvcTest(GridController.class)
class GridControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GridClockRunner runner;

    private static GridStatus sampleStatus() {
        return new GridStatus(144L, "12:00", 0L, 5L, -0.1, true, 900.0, 1000.0);
    }

    @Test
    void statusReportsTheClock() throws Exception {
        given(runner.snapshot()).willReturn(sampleStatus());

        mockMvc.perform(get("/api/grid/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickNumber").value(144))
                .andExpect(jsonPath("$.simulatedTime").value("12:00"))
                .andExpect(jsonPath("$.simulatedDay").value(0))
                .andExpect(jsonPath("$.tickIntervalSeconds").value(5))
                .andExpect(jsonPath("$.frequencyDeviation").value(-0.1))
                .andExpect(jsonPath("$.autoControlEnabled").value(true))
                .andExpect(jsonPath("$.totalSupplyKw").value(900.0))
                .andExpect(jsonPath("$.totalDemandKw").value(1000.0));
    }

    @Test
    void settingTheDeviationAppliesItAndReturnsTheNewStatus() throws Exception {
        given(runner.setFrequencyDeviation(-0.1)).willReturn(sampleStatus());

        mockMvc.perform(put("/api/grid/frequency-deviation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"frequencyDeviation\":-0.1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frequencyDeviation").value(-0.1));

        verify(runner).setFrequencyDeviation(-0.1);
    }

    @Test
    void aDeviationBeyondTheBoundsIsRejected() throws Exception {
        mockMvc.perform(put("/api/grid/frequency-deviation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"frequencyDeviation\":5.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(runner, never()).setFrequencyDeviation(ArgumentMatchers.anyDouble());
    }

    /** Omitting the field must not be read as a silent return to nominal. */
    @Test
    void anEmptyDeviationBodyIsRejected() throws Exception {
        mockMvc.perform(put("/api/grid/frequency-deviation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());

        verify(runner, never()).setFrequencyDeviation(ArgumentMatchers.anyDouble());
    }
}
