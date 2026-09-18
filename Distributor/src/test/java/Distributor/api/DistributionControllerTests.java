package Distributor.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import Distributor.distribution.GridStateTracker;

/**
 * Contract of the status endpoint: a slice with the tracker mocked, so this runs with no Kafka and
 * no Postgres.
 */
@WebMvcTest(DistributionController.class)
class DistributionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GridStateTracker state;

    @Test
    void reportsTotalsAndTheBalanceBetweenThem() throws Exception {
        given(state.totalSupplyKw()).willReturn(900.0);
        given(state.totalDemandKw()).willReturn(1000.0);
        given(state.trackedPlantCount()).willReturn(3);
        given(state.trackedZoneCount()).willReturn(2);

        mockMvc.perform(get("/api/distribution/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSupplyKw").value(900.0))
                .andExpect(jsonPath("$.totalDemandKw").value(1000.0))
                .andExpect(jsonPath("$.balanceKw").value(-100.0))
                .andExpect(jsonPath("$.trackedPlantCount").value(3))
                .andExpect(jsonPath("$.trackedZoneCount").value(2));
    }

    @Test
    void reportsZeroesBeforeAnythingHasArrived() throws Exception {
        given(state.totalSupplyKw()).willReturn(0.0);
        given(state.totalDemandKw()).willReturn(0.0);
        given(state.trackedPlantCount()).willReturn(0);
        given(state.trackedZoneCount()).willReturn(0);

        mockMvc.perform(get("/api/distribution/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceKw").value(0.0));
    }
}
