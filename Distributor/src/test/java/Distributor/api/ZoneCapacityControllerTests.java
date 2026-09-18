package Distributor.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import Distributor.distribution.GridStateTracker;
import Distributor.distribution.ZoneCapacityNotFoundException;
import Distributor.distribution.ZoneCapacityService;
import Distributor.model.ZoneCapacity;

/**
 * Contract of zone-capacity management: a slice with the service and tracker mocked, so this runs
 * with no Kafka and no Postgres.
 */
@WebMvcTest(ZoneCapacityController.class)
class ZoneCapacityControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZoneCapacityService service;

    @MockitoBean
    private GridStateTracker state;

    @Test
    void listsEveryZoneWithItsCurrentDemandAndOverCapacityFlag() throws Exception {
        given(service.list()).willReturn(List.of(new ZoneCapacity("Z-N", "North", 500.0)));
        given(state.demandKwFor("Z-N")).willReturn(650.0);

        mockMvc.perform(get("/api/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zoneId").value("Z-N"))
                .andExpect(jsonPath("$[0].capacityKw").value(500.0))
                .andExpect(jsonPath("$[0].currentDemandKw").value(650.0))
                .andExpect(jsonPath("$[0].overCapacity").value(true));
    }

    @Test
    void demandWithinCapacityIsNotFlaggedOver() throws Exception {
        given(service.list()).willReturn(List.of(new ZoneCapacity("Z-N", "North", 500.0)));
        given(state.demandKwFor("Z-N")).willReturn(300.0);

        mockMvc.perform(get("/api/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].overCapacity").value(false));
    }

    @Test
    void createsAZoneCapacity() throws Exception {
        given(service.upsert("Z-N", "North", 500.0)).willReturn(new ZoneCapacity("Z-N", "North", 500.0));
        given(state.demandKwFor("Z-N")).willReturn(0.0);

        mockMvc.perform(post("/api/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneId\":\"Z-N\",\"zoneName\":\"North\",\"capacityKw\":500.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value("Z-N"))
                .andExpect(jsonPath("$.capacityKw").value(500.0));
    }

    @Test
    void aBlankZoneIdIsRejected() throws Exception {
        mockMvc.perform(post("/api/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneId\":\"\",\"zoneName\":\"North\",\"capacityKw\":500.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void deletingAnUnknownZoneReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new ZoneCapacityNotFoundException("Z-GHOST"))
                .when(service).delete("Z-GHOST");

        mockMvc.perform(delete("/api/zones/Z-GHOST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }
}
