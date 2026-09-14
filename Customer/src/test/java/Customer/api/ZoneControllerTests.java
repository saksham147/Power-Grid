package Customer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import Customer.application.ZoneRepository;
import Customer.domain.DemandProfile;
import Customer.domain.Zone;
import reactor.core.publisher.Mono;

/**
 * Contract of the zone management endpoints: a slice with the repository mocked, so this runs
 * with no Redis.
 */
@WebFluxTest(ZoneController.class)
class ZoneControllerTests {

    private static final Zone NORTH = new Zone(
            "Z-NORTH", "North Residential", 250_000, DemandProfile.RESIDENTIAL, 1.1, 0.35, 0.06);

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ZoneRepository zones;

    @Test
    void listsEveryConfiguredZone() {
        given(zones.findAll()).willReturn(List.of(NORTH));

        client.get().uri("/api/zones").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].zoneId").isEqualTo("Z-NORTH")
                .jsonPath("$[0].name").isEqualTo("North Residential")
                .jsonPath("$[0].customers").isEqualTo(250_000)
                .jsonPath("$[0].profile").isEqualTo("RESIDENTIAL")
                .jsonPath("$[0].baseKwPerCustomer").isEqualTo(1.1);
    }

    @Test
    void createsAZoneAndReturns201WithALocationHeader() {
        given(zones.save(any())).willReturn(Mono.empty());

        client.post().uri("/api/zones")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"zoneId":"Z-SOUTH","name":"South Residential","customers":1000,
                         "profile":"RESIDENTIAL","baseKwPerCustomer":1.2,
                         "customerVariability":0.3,"zoneVariability":0.05}""")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/zones/Z-SOUTH")
                .expectBody()
                .jsonPath("$.zoneId").isEqualTo("Z-SOUTH")
                .jsonPath("$.customers").isEqualTo(1000);

        verify(zones).save(new Zone("Z-SOUTH", "South Residential", 1000, DemandProfile.RESIDENTIAL,
                1.2, 0.3, 0.05));
    }

    /** Present, well-typed fields that fail the business rule -- not a malformed body. */
    @Test
    void rejectsAZoneWithInvalidValues() {
        client.post().uri("/api/zones")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"zoneId":"","name":"","customers":0,
                         "profile":"RESIDENTIAL","baseKwPerCustomer":0,
                         "customerVariability":0,"zoneVariability":0}""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.details").isArray()
                .jsonPath("$.details[0]").exists();
    }

    @Test
    void deletingAZoneReturns204() {
        given(zones.delete("Z-NORTH")).willReturn(Mono.empty());

        client.delete().uri("/api/zones/Z-NORTH").exchange()
                .expectStatus().isNoContent();

        verify(zones).delete("Z-NORTH");
    }
}
