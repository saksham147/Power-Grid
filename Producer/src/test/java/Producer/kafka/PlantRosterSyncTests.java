package Producer.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import Producer.event.PlantRosterEvent;
import Producer.model.PlantType;
import Producer.model.PowerPlant;
import Producer.model.PowerPlantRepository;

/**
 * Every plant in the table -- active or not -- goes out as a not-removed roster event, carrying
 * the plant's own current values; and a disabled sync schedules nothing.
 */
class PlantRosterSyncTests {

    private final PowerPlantRepository plants = mock(PowerPlantRepository.class);
    private final PlantRosterPublisher publisher = mock(PlantRosterPublisher.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    private static PowerPlant plant(long id, PlantType type, double capacityMw, boolean active) {
        var plant = new PowerPlant("P" + id, type, capacityMw, 0.0, 0.0);
        ReflectionTestUtils.setField(plant, "id", id);
        plant.setActive(active);
        return plant;
    }

    private PlantRosterSync sync(boolean enabled) {
        return new PlantRosterSync(plants, publisher, scheduler, enabled, Duration.ofMinutes(10));
    }

    @Test
    void announcesEveryPlantIncludingInactiveOnes() {
        given(plants.findAll()).willReturn(List.of(
                plant(1, PlantType.THERMAL, 900.0, true),
                plant(2, PlantType.WIND, 150.0, false)));

        int announced = sync(true).syncAll();

        assertThat(announced).isEqualTo(2);
        var events = ArgumentCaptor.forClass(PlantRosterEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(events.capture());
        assertThat(events.getAllValues()).satisfiesExactly(
                e -> {
                    assertThat(e.plantId()).isEqualTo(1L);
                    assertThat(e.type()).isEqualTo(PlantType.THERMAL);
                    assertThat(e.capacityMw()).isEqualTo(900.0);
                    assertThat(e.active()).isTrue();
                    assertThat(e.removed()).isFalse();
                },
                e -> {
                    assertThat(e.plantId()).isEqualTo(2L);
                    assertThat(e.active()).isFalse();
                    assertThat(e.removed()).isFalse();
                });
    }

    @Test
    void anEmptyTablePublishesNothing() {
        given(plants.findAll()).willReturn(List.of());

        assertThat(sync(true).syncAll()).isZero();
        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startsTheScheduleWhenEnabledAndNotWhenDisabled() {
        sync(true).startOnBoot();
        verify(scheduler).scheduleWithFixedDelay(org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10)));

        var disabledScheduler = mock(TaskScheduler.class);
        new PlantRosterSync(plants, publisher, disabledScheduler, false, Duration.ofMinutes(10)).startOnBoot();
        verify(disabledScheduler, never()).scheduleWithFixedDelay(org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }
}
