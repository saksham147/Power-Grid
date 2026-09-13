package Producer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import Producer.history.GenerationHistoryQuery;
import Producer.history.HistoryPoint;

/** HTTP contract of the history endpoint, with the query mocked. */
@WebMvcTest(GenerationHistoryController.class)
class GenerationHistoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GenerationHistoryQuery query;

    @Test
    void defaultsToTheLastDayAndOneSimulatedDayOfPoints() throws Exception {
        given(query.history(anyLong(), any(), any(), anyInt())).willReturn(List.of());

        mockMvc.perform(get("/api/plants/6/history")).andExpect(status().isOk());

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(query).history(org.mockito.ArgumentMatchers.eq(6L), from.capture(), to.capture(),
                org.mockito.ArgumentMatchers.eq(GenerationHistoryController.DEFAULT_LIMIT));

        assertThat(Duration.between(from.getValue(), to.getValue())).isEqualTo(Duration.ofHours(24));
        assertThat(Duration.between(to.getValue(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void passesAnExplicitRangeAndLimitThrough() throws Exception {
        Instant from = Instant.parse("2026-09-10T00:00:00Z");
        Instant to = Instant.parse("2026-09-11T00:00:00Z");
        given(query.history(6L, from, to, 50)).willReturn(List.of(
                new HistoryPoint(to.minusSeconds(60), HistoryPoint.Resolution.ROLLUP,
                        410.0, 395.0, 425.0, 410.0 * 12 * 5 / 60, 12, 100, 111)));

        mockMvc.perform(get("/api/plants/6/history")
                .param("from", from.toString())
                .param("to", to.toString())
                .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resolution").value("ROLLUP"))
                .andExpect(jsonPath("$[0].samples").value(12))
                .andExpect(jsonPath("$[0].minOutputMw").value(395.0));
    }

    @Test
    void rejectsALimitOutsideTheCap() throws Exception {
        mockMvc.perform(get("/api/plants/6/history").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/plants/6/history")
                .param("limit", String.valueOf(GenerationHistoryController.MAX_LIMIT + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(query, never()).history(anyLong(), any(), any(), anyInt());
    }

    @Test
    void rejectsAnEmptyOrBackwardsRange() throws Exception {
        mockMvc.perform(get("/api/plants/6/history")
                .param("from", "2026-09-11T00:00:00Z")
                .param("to", "2026-09-10T00:00:00Z"))
                .andExpect(status().isBadRequest());

        verify(query, never()).history(anyLong(), any(), any(), anyInt());
    }
}
