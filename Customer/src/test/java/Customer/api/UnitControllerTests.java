package Customer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import Customer.application.ConsumerUnitRepository;
import Customer.application.CurrentDemand;
import Customer.application.DemandStateStore;
import Customer.domain.ConsumerUnit;
import Customer.domain.DemandProfile;
import Customer.domain.SimulationClock;
import reactor.core.publisher.Mono;

/**
 * Contract of the unit management endpoints: a slice with the repository and state store mocked,
 * so this runs with no Redis.
 */
@WebFluxTest(UnitController.class)
class UnitControllerTests {

    private static final ConsumerUnit NORTH_A = new ConsumerUnit(
            "U-NORTH-A", "Z-NORTH", "North Homes A", DemandProfile.RESIDENTIAL, 800);

    @TestConfiguration
    static class Config {
        @Bean
        SimulationClock clock() {
            return new SimulationClock(5, 1);
        }
    }

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ConsumerUnitRepository units;

    @MockitoBean
    private DemandStateStore store;

    @Test
    void listsEveryUnitWithItsLiveDemand() {
        given(units.findAll()).willReturn(List.of(NORTH_A));
        given(store.current()).willReturn(Mono.just(
                new CurrentDemand(144, Instant.now(), 500.0, Map.of("Z-NORTH", 300.0))));

        client.get().uri("/api/units").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].unitId").isEqualTo("U-NORTH-A")
                .jsonPath("$[0].zoneId").isEqualTo("Z-NORTH")
                .jsonPath("$[0].type").isEqualTo("RESIDENTIAL")
                .jsonPath("$[0].capacityKw").isEqualTo(800.0)
                .jsonPath("$[0].demandKw").exists();
    }

    @Test
    void createsAUnitAndReturns201WithALocationHeader() {
        given(units.save(any())).willReturn(Mono.empty());

        client.post().uri("/api/units")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"unitId":"U-NEW","zoneId":"Z-NORTH","name":"New House",
                         "type":"RESIDENTIAL","capacityKw":500}""")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/units/U-NEW")
                .expectBody()
                .jsonPath("$.unitId").isEqualTo("U-NEW")
                .jsonPath("$.zoneId").isEqualTo("Z-NORTH");

        verify(units).save(new ConsumerUnit("U-NEW", "Z-NORTH", "New House", DemandProfile.RESIDENTIAL, 500));
    }

    @Test
    void rejectsAUnitWithInvalidValues() {
        client.post().uri("/api/units")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"unitId":"","zoneId":"","name":"","type":"RESIDENTIAL","capacityKw":0}""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.details").isArray()
                .jsonPath("$.details[0]").exists();
    }

    @Test
    void upgradesAUnitAndReturnsItsNewDetails() {
        given(units.save(any())).willReturn(Mono.empty());

        client.put().uri("/api/units/U-NORTH-A")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"zoneId":"Z-NORTH","name":"North Homes A (expanded)",
                         "type":"RESIDENTIAL","capacityKw":950}""")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.unitId").isEqualTo("U-NORTH-A")
                .jsonPath("$.capacityKw").isEqualTo(950.0);

        verify(units).save(new ConsumerUnit(
                "U-NORTH-A", "Z-NORTH", "North Homes A (expanded)", DemandProfile.RESIDENTIAL, 950));
    }

    @Test
    void deletingAUnitReturns204() {
        given(units.delete("U-NORTH-A")).willReturn(Mono.empty());

        client.delete().uri("/api/units/U-NORTH-A").exchange()
                .expectStatus().isNoContent();

        verify(units).delete("U-NORTH-A");
    }
}
