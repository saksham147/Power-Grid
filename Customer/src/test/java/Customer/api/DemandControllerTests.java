package Customer.api;

import static org.mockito.BDDMockito.given;

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

import Customer.application.CurrentDemand;
import Customer.application.DemandStateStore;
import Customer.application.ZoneRepository;
import Customer.domain.DemandProfile;
import Customer.domain.SimulationClock;
import Customer.domain.Zone;
import reactor.core.publisher.Mono;

/**
 * Contract of {@code GET /api/demand}: a slice with the store and the zone repository mocked, so
 * this runs with no Redis and no Kafka. {@code CustomerApplicationTests} boots the full context
 * and already needs both.
 */
@WebFluxTest(DemandController.class)
class DemandControllerTests {

    private static final Zone NORTH = new Zone(
            "Z-NORTH", "North Residential", 250_000, DemandProfile.RESIDENTIAL, 1.1, 0.35, 0.06);
    private static final Zone EAST = new Zone(
            "Z-EAST", "East Industrial", 800, DemandProfile.INDUSTRIAL, 240.0, 0.12, 0.03);

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
    private DemandStateStore store;

    @MockitoBean
    private ZoneRepository zones;

    @Test
    void reportsEveryConfiguredZoneJoinedAgainstItsLatestDemand() {
        Instant at = Instant.parse("2026-09-13T12:00:00Z");
        given(zones.findAll()).willReturn(List.of(NORTH, EAST));
        given(store.current()).willReturn(Mono.just(
                new CurrentDemand(144, at, 500.0, Map.of("Z-NORTH", 300.0, "Z-EAST", 200.0))));

        client.get().uri("/api/demand").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tick").isEqualTo(144)
                .jsonPath("$.simulatedTime").isEqualTo("12:00")
                .jsonPath("$.simulatedDay").isEqualTo(0)
                .jsonPath("$.updatedAt").isEqualTo(at.toString())
                .jsonPath("$.totalKw").isEqualTo(500.0)
                .jsonPath("$.zones[0].zoneId").isEqualTo("Z-NORTH")
                .jsonPath("$.zones[0].name").isEqualTo("North Residential")
                .jsonPath("$.zones[0].customers").isEqualTo(250_000)
                .jsonPath("$.zones[0].profile").isEqualTo("RESIDENTIAL")
                .jsonPath("$.zones[0].demandKw").isEqualTo(300.0)
                .jsonPath("$.zones[1].zoneId").isEqualTo("Z-EAST")
                .jsonPath("$.zones[1].demandKw").isEqualTo(200.0);
    }

    /** Before the first tick, Redis has nothing -- the response still lists every configured zone. */
    @Test
    void beforeTheFirstTickEveryZoneReportsZero() {
        given(zones.findAll()).willReturn(List.of(NORTH, EAST));
        given(store.current()).willReturn(Mono.empty());

        client.get().uri("/api/demand").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tick").isEqualTo(0)
                .jsonPath("$.simulatedTime").isEqualTo("00:00")
                .jsonPath("$.updatedAt").doesNotExist()
                .jsonPath("$.totalKw").isEqualTo(0.0)
                .jsonPath("$.zones.length()").isEqualTo(2)
                .jsonPath("$.zones[0].demandKw").isEqualTo(0.0)
                .jsonPath("$.zones[1].demandKw").isEqualTo(0.0);
    }

    /** A zone missing from the store's answer (added after its last write) defaults, not errors. */
    @Test
    void aZoneMissingFromTheStoreDefaultsToZero() {
        given(zones.findAll()).willReturn(List.of(NORTH, EAST));
        given(store.current()).willReturn(Mono.just(
                new CurrentDemand(1, Instant.parse("2026-09-13T00:00:05Z"), 300.0, Map.of("Z-NORTH", 300.0))));

        client.get().uri("/api/demand").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.zones[1].zoneId").isEqualTo("Z-EAST")
                .jsonPath("$.zones[1].demandKw").isEqualTo(0.0);
    }
}
