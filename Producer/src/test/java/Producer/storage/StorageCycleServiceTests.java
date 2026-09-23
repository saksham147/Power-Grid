package Producer.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import Producer.event.GridTickEvent;
import Producer.event.StorageOutputEvent;
import Producer.kafka.StorageOutputPublisher;
import Producer.model.StorageKind;
import Producer.model.StorageUnit;
import Producer.model.StorageUnitRepository;

/** The charge/discharge physics: direction follows frequency deviation, magnitude is capped by
 *  both the rate limit and however much energy is actually available/has room. */
class StorageCycleServiceTests {

    private static final double HOURS_PER_TICK = 5.0 / 60.0;

    @Test
    void aDeficitDischargesAtTheRateLimit() {
        // 100 kWh battery, half charged (50 kWh), 600 kW max discharge -> 600*(5/60)=50 kWh
        // room to discharge this tick, exactly what's available.
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);

        double netKw = StorageCycleService.applyTick(unit, -0.05);

        assertThat(netKw).isCloseTo(600.0, within(1e-9));
        assertThat(unit.getStateOfChargeKwh()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void dischargeCannotExceedWhatIsActuallyStored() {
        // Only 10 kWh stored; the 600 kW rate would ask for 50 kWh, more than exists.
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);
        unit.setStateOfChargeKwh(10.0);

        double netKw = StorageCycleService.applyTick(unit, -0.05);

        assertThat(netKw).isCloseTo(10.0 / HOURS_PER_TICK, within(1e-9));
        assertThat(unit.getStateOfChargeKwh()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void aSurplusChargesAndReportsNegativeNetKw() {
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);
        unit.setStateOfChargeKwh(0.0);

        double netKw = StorageCycleService.applyTick(unit, 0.05);

        assertThat(netKw).isCloseTo(-600.0, within(1e-9));
        assertThat(unit.getStateOfChargeKwh()).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void chargeCannotExceedAvailableRoom() {
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);
        unit.setStateOfChargeKwh(95.0);

        double netKw = StorageCycleService.applyTick(unit, 0.05);

        assertThat(netKw).isCloseTo(-(5.0 / HOURS_PER_TICK), within(1e-9));
        assertThat(unit.getStateOfChargeKwh()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    void withinTheDeadbandTheUnitStaysIdle() {
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);
        double before = unit.getStateOfChargeKwh();

        double netKw = StorageCycleService.applyTick(unit, 0.01);

        assertThat(netKw).isZero();
        assertThat(unit.getStateOfChargeKwh()).isEqualTo(before);
    }

    // ---- handleTick: the persistence this method is responsible for, not just the pure physics
    // above -- see handleTick's own doc comment for the bug this guards against (a charge/discharge
    // that computed the right event but never actually reached the database).

    /** A template that simply runs the callback, so the service's own logic executes for real. */
    private static TransactionTemplate passThrough() {
        var template = mock(TransactionTemplate.class);
        given(template.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        return template;
    }

    @Test
    void handleTickRunsInsideATransactionSoTheChargeIsNotJustComputedButPersisted() {
        var unit = new StorageUnit("B1", StorageKind.BATTERY, 100, 600, 600);
        ReflectionTestUtils.setField(unit, "id", 1L);
        unit.setStateOfChargeKwh(0.0);

        var repository = mock(StorageUnitRepository.class);
        given(repository.findByActiveTrue()).willReturn(List.of(unit));
        var publisher = mock(StorageOutputPublisher.class);
        var template = passThrough();

        var service = new StorageCycleService(repository, publisher, template);
        List<StorageOutputEvent> events = service.handleTick(new GridTickEvent(42L, 0.05));

        verify(template).execute(any());
        // The mutation is on the same managed-in-a-real-run entity the repository returned --
        // proof the transaction actually ran, not just that the right number was computed.
        assertThat(unit.getStateOfChargeKwh()).isCloseTo(50.0, within(1e-9));
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.unitId()).isEqualTo(1L);
            assertThat(e.tickNumber()).isEqualTo(42L);
            assertThat(e.netKw()).isCloseTo(-600.0, within(1e-9));
        });
        verify(publisher, times(1)).publish(events.get(0));
    }

    @Test
    void noActiveUnitsPublishesNothing() {
        var repository = mock(StorageUnitRepository.class);
        given(repository.findByActiveTrue()).willReturn(List.of());
        var publisher = mock(StorageOutputPublisher.class);

        var service = new StorageCycleService(repository, publisher, passThrough());
        List<StorageOutputEvent> events = service.handleTick(new GridTickEvent(1L, 0.05));

        assertThat(events).isEmpty();
        verify(publisher, org.mockito.Mockito.never()).publish(any());
    }
}
