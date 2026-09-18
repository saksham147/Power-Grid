package Distributor.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import Distributor.event.ZoneCapacityEvent;
import Distributor.model.ZoneCapacity;
import Distributor.model.ZoneCapacityRepository;

/**
 * The use case, driven against a mocked repository (a Spring Data interface, not worth hand-faking
 * -- see {@code Billing.billing.WalletSpendingServiceTests} for the same reasoning) and a fake
 * publisher.
 */
class ZoneCapacityServiceTests {

    @Test
    void creatingANewZoneSavesItAndPublishesTheCapacity() {
        var repository = mock(ZoneCapacityRepository.class);
        var published = new ArrayList<ZoneCapacityEvent>();
        given(repository.findById("Z-N")).willReturn(Optional.empty());
        var service = new ZoneCapacityService(repository, published::add);

        service.upsert("Z-N", "North", 500.0);

        verify(repository).save(any(ZoneCapacity.class));
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().zoneId()).isEqualTo("Z-N");
        assertThat(published.getFirst().capacityKw()).isEqualTo(500.0);
    }

    @Test
    void updatingAnExistingZoneReusesTheRowRatherThanCreatingASecondOne() {
        var repository = mock(ZoneCapacityRepository.class);
        var existing = new ZoneCapacity("Z-N", "North", 500.0);
        given(repository.findById("Z-N")).willReturn(Optional.of(existing));
        var published = new ArrayList<ZoneCapacityEvent>();
        var service = new ZoneCapacityService(repository, published::add);

        ZoneCapacity result = service.upsert("Z-N", "North Renamed", 750.0);

        assertThat(result).isSameAs(existing);
        assertThat(result.getCapacityKw()).isEqualTo(750.0);
        assertThat(result.getZoneName()).isEqualTo("North Renamed");
        assertThat(published.getFirst().capacityKw()).isEqualTo(750.0);
    }

    @Test
    void deletingAZoneRemovesItAndPublishesARemovalWithNoCapacity() {
        var repository = mock(ZoneCapacityRepository.class);
        given(repository.findById("Z-N")).willReturn(Optional.of(new ZoneCapacity("Z-N", "North", 500.0)));
        var published = new ArrayList<ZoneCapacityEvent>();
        var service = new ZoneCapacityService(repository, published::add);

        service.delete("Z-N");

        verify(repository).deleteById("Z-N");
        assertThat(published.getFirst().capacityKw()).isNull();
    }

    @Test
    void deletingAZoneWithNoCapacityAssignedThrowsRatherThanPublishingAnything() {
        var repository = mock(ZoneCapacityRepository.class);
        given(repository.findById("Z-GHOST")).willReturn(Optional.empty());
        var published = new ArrayList<ZoneCapacityEvent>();
        var service = new ZoneCapacityService(repository, published::add);

        assertThatThrownBy(() -> service.delete("Z-GHOST")).isInstanceOf(ZoneCapacityNotFoundException.class);

        assertThat(published).isEmpty();
        verify(repository, never()).deleteById(any());
    }

    @Test
    void listsEveryZoneCapacity() {
        var repository = mock(ZoneCapacityRepository.class);
        given(repository.findAll()).willReturn(List.of(new ZoneCapacity("Z-N", "North", 500.0)));
        var service = new ZoneCapacityService(repository, event -> {
        });

        assertThat(service.list()).hasSize(1);
    }
}
