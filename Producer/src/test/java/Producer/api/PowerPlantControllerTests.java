package Producer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import Producer.generation.ForecastService;
import Producer.kafka.PlantRosterPublisher;
import Producer.model.PlantType;
import Producer.model.PowerPlant;
import Producer.model.PowerPlantRepository;
import Producer.simulation.SimulationRunner;

/**
 * Contract of the removal and upgrade endpoints.
 *
 * <p>
 * A slice with the repository mocked, so it runs without Postgres. The rating
 * invariants are
 * exercised against the real {@link PowerPlant} rather than a stub, because they
 * live on the
 * entity and a mock would assert nothing about them.
 */
@WebMvcTest(PowerPlantController.class)
class PowerPlantControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PowerPlantRepository repository;

    @MockitoBean
    private PlantRosterPublisher rosterPublisher;

    @MockitoBean
    private ForecastService forecastService;

    @MockitoBean
    private SimulationRunner simulationRunner;

    private static PowerPlant thermal() {
        return new PowerPlant("Ratnagiri Thermal Unit 1", PlantType.THERMAL, 500, 200, 400);
    }

    // ---------- removal ----------

    @Test
    void deletingAPlantReturns204() throws Exception {
        given(repository.findById(1L)).willReturn(Optional.of(thermal()));

        mockMvc.perform(delete("/api/plants/1")).andExpect(status().isNoContent());

        verify(repository).deleteById(1L);
    }

    /** findById is how the roster-removed event gets the plant's last known rating. */
    @Test
    void deletingAnUnknownPlantIs404AndDeletesNothing() throws Exception {
        given(repository.findById(99L)).willReturn(Optional.empty());

        mockMvc.perform(delete("/api/plants/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(repository, never()).deleteById(any());
    }

    // ---------- upgrade ----------

    @Test
    void upgradingAppliesTheNewRatings() throws Exception {
        given(repository.findById(1L)).willReturn(Optional.of(thermal()));

        mockMvc.perform(put("/api/plants/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Ratnagiri Thermal Unit 1 (uprated)","capacityMw":600,
                         "minOutputMw":240,"baseOutputMw":480}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ratnagiri Thermal Unit 1 (uprated)"))
                .andExpect(jsonPath("$.capacityMw").value(600.0))
                .andExpect(jsonPath("$.minOutputMw").value(240.0))
                .andExpect(jsonPath("$.baseOutputMw").value(480.0))
                // Type is not in the request and must survive untouched.
                .andExpect(jsonPath("$.type").value("THERMAL"));
    }

    @Test
    void upgradingAnUnknownPlantIs404() throws Exception {
        given(repository.findById(99L)).willReturn(Optional.empty());

        mockMvc.perform(put("/api/plants/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Nowhere","capacityMw":10,"minOutputMw":0,"baseOutputMw":0}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMinimumAboveCapacityIsRejected() throws Exception {
        given(repository.findById(1L)).willReturn(Optional.of(thermal()));

        mockMvc.perform(put("/api/plants/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Bad","capacityMw":100,"minOutputMw":500,"baseOutputMw":50}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("minOutputMw (500.0) cannot exceed capacityMw (100.0)"));
    }

    @Test
    void aBlankNameOrNonPositiveCapacityIsRejectedPerField() throws Exception {
        mockMvc.perform(put("/api/plants/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"","capacityMw":-5,"minOutputMw":0,"baseOutputMw":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    // ---------- the entity's own invariants ----------

    /**
     * Downgrading below the last tick's output must not leave a figure the plant
     * could not produce.
     */
    @Test
    void downgradingCapacityClampsCurrentOutput() {
        PowerPlant plant = thermal();
        plant.setCurrentOutputMw(425);

        plant.upgrade("Derated", 300, 120, 250);

        assertThat(plant.getCurrentOutputMw()).isEqualTo(300.0);
        assertThat(plant.getCapacityMw()).isEqualTo(300.0);
    }

    @Test
    void aRejectedUpgradeLeavesThePlantUntouched() {
        PowerPlant plant = thermal();

        assertThatThrownBy(() -> plant.upgrade("Bad", 100, 500, 50))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(plant.getCapacityMw()).isEqualTo(500.0);
        assertThat(plant.getName()).isEqualTo("Ratnagiri Thermal Unit 1");
    }
}
