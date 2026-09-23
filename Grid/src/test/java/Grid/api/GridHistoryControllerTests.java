package Grid.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import Grid.model.TickRecord;
import Grid.model.TickRecordRepository;

/** Contract of the history endpoint: a slice with the repository mocked, so this runs with no
 *  database. The one thing that isn't obvious from the repository call alone -- oldest-first,
 *  the opposite of how the repository itself reads rows out -- is what these tests exist to pin. */
@WebMvcTest(GridHistoryController.class)
class GridHistoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TickRecordRepository repository;

    private static TickRecord record(long tick) {
        return new TickRecord(tick, "12:00", 0L, -0.1, 900.0, 1000.0, true, Instant.parse("2026-01-01T12:00:00Z"));
    }

    @Test
    void returnsPointsOldestFirstEvenThoughTheRepositoryReadsThemNewestFirst() throws Exception {
        // The repository is documented newest-first; the endpoint must reverse that for a chart.
        given(repository.findRecent(any())).willReturn(List.of(record(3), record(2), record(1)));

        mockMvc.perform(get("/api/grid/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tickNumber", is(1)))
                .andExpect(jsonPath("$[1].tickNumber", is(2)))
                .andExpect(jsonPath("$[2].tickNumber", is(3)))
                .andExpect(jsonPath("$[0].frequencyDeviation", is(-0.1)))
                .andExpect(jsonPath("$[0].totalSupplyKw", is(900.0)))
                .andExpect(jsonPath("$[0].loadExceeded", is(true)));
    }

    @Test
    void defaultsToOneSimulatedDayOfTicks() throws Exception {
        given(repository.findRecent(any())).willReturn(List.of());

        mockMvc.perform(get("/api/grid/history"));

        var limit = ArgumentCaptor.forClass(Limit.class);
        verify(repository).findRecent(limit.capture());
        assertThat(limit.getValue().max()).isEqualTo(288);
    }

    @Test
    void aCustomLimitIsPassedThrough() throws Exception {
        given(repository.findRecent(any())).willReturn(List.of());

        mockMvc.perform(get("/api/grid/history?limit=50"));

        var limit = ArgumentCaptor.forClass(Limit.class);
        verify(repository).findRecent(limit.capture());
        assertThat(limit.getValue().max()).isEqualTo(50);
    }

    @Test
    void anEmptyHistoryIsAnEmptyListNotAnError() throws Exception {
        given(repository.findRecent(any())).willReturn(List.of());

        mockMvc.perform(get("/api/grid/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
