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

import Customer.application.ConsumerUnitRepository;
import Customer.application.ZoneRepository;
import Customer.domain.Zone;
import reactor.core.publisher.Mono;

/**
 * Contract of the zone management endpoints: a slice with the repositories mocked, so this runs
 * with no Redis.
 */
@WebFluxTest(ZoneController.class)
class ZoneControllerTests {

    private static final Zone NORTH = new Zone("Z-NORTH", "North Residential");

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ZoneRepository zones;

    @MockitoBean
    private ConsumerUnitRepository units;

    @Test
    void listsEveryConfiguredZone() {
        given(zones.findAll()).willReturn(List.of(NORTH));

        client.get().uri("/api/zones").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].zoneId").isEqualTo("Z-NORTH")
                .jsonPath("$[0].name").isEqualTo("North Residential");
    }

    @Test
    void createsAZoneAndReturns201WithALocationHeader() {
        given(zones.save(any())).willReturn(Mono.empty());

        client.post().uri("/api/zones")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"zoneId":"Z-SOUTH","name":"South Residential"}""")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/zones/Z-SOUTH")
                .expectBody()
                .jsonPath("$.zoneId").isEqualTo("Z-SOUTH")
                .jsonPath("$.name").isEqualTo("South Residential");

        verify(zones).save(new Zone("Z-SOUTH", "South Residential"));
    }

    /** Present, well-typed fields that fail the business rule -- not a malformed body. */
    @Test
    void rejectsAZoneWithInvalidValues() {
        client.post().uri("/api/zones")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"zoneId":"","name":""}""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.details").isArray()
                .jsonPath("$.details[0]").exists();
    }

    @Test
    void upgradesAZoneAndReturnsItsNewDetails() {
        given(zones.save(any())).willReturn(Mono.empty());

        client.put().uri("/api/zones/Z-NORTH")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"North Residential (renamed)"}""")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.zoneId").isEqualTo("Z-NORTH")
                .jsonPath("$.name").isEqualTo("North Residential (renamed)");

        verify(zones).save(new Zone("Z-NORTH", "North Residential (renamed)"));
    }

    @Test
    void deletingAZoneReturns204AndCascadesToItsUnits() {
        given(units.deleteByZoneId("Z-NORTH")).willReturn(Mono.empty());
        given(zones.delete("Z-NORTH")).willReturn(Mono.empty());

        client.delete().uri("/api/zones/Z-NORTH").exchange()
                .expectStatus().isNoContent();

        verify(units).deleteByZoneId("Z-NORTH");
        verify(zones).delete("Z-NORTH");
    }
}
