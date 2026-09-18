package Producer.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Producer.event.GridTickEvent;
import Producer.event.StorageOutputEvent;
import Producer.kafka.StorageOutputPublisher;
import Producer.model.StorageUnit;
import Producer.model.StorageUnitRepository;
import Producer.simulation.SimulationClock;

/**
 * Charges and discharges every active {@link StorageUnit} automatically, once per tick, reacting
 * to {@code frequencyDeviation} -- the same continuous supply/demand-gap signal {@code
 * ThermalGenerationStrategy}'s governor already reacts to, rather than a separate call to Grid's
 * own deficit boolean. A negative deviation (demand outrunning supply) discharges; a positive one
 * (surplus) charges; inside the deadband, units sit idle.
 *
 * <p>
 * Its own {@code @KafkaListener} on {@code grid.tick} -- not folded into {@code GenerationService
 * #handleTick} -- because storage is a genuinely separate concern from generation with its own
 * repository and its own output topic; see {@code Producer.config.KafkaConfig} for why this gets
 * a dedicated consumer group rather than sharing {@code GenerationService}'s.
 */
@Service
public class StorageCycleService {

    private static final Logger log = LoggerFactory.getLogger(StorageCycleService.class);

    /** Below this deviation magnitude, storage stays idle -- without a deadband, tiny frequency
     *  noise would cycle every unit charge/discharge every tick for no real benefit. */
    private static final double DEADBAND_HZ = 0.02;

    private static final double HOURS_PER_TICK = SimulationClock.SIMULATED_MINUTES_PER_TICK / 60.0;

    private final StorageUnitRepository repository;
    private final StorageOutputPublisher publisher;

    public StorageCycleService(StorageUnitRepository repository, StorageOutputPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @KafkaListener(topics = "grid.tick", containerFactory = "storageGridTickListenerContainerFactory",
            autoStartup = "${producer.simulation.autostart:true}")
    void onGridTick(GridTickEvent tick) {
        try {
            handleTick(tick);
        } catch (Exception e) {
            // Swallowed for the same reason SimulationRunner.onGridTick does this: an escaping
            // exception wedges this listener's partition and silently stops every future tick.
            log.error("Storage cycle failed for tick {}; the clock continues", tick.tickNumber(), e);
        }
    }

    @Transactional
    List<StorageOutputEvent> handleTick(GridTickEvent tick) {
        List<StorageUnit> units = repository.findByActiveTrue();
        List<StorageOutputEvent> events = new ArrayList<>(units.size());
        Instant timestamp = Instant.now();

        for (StorageUnit unit : units) {
            double netKw = applyTick(unit, tick.frequencyDeviation());
            events.add(new StorageOutputEvent(unit.getId(), tick.tickNumber(), netKw, timestamp));
        }

        events.forEach(publisher::publish);
        return events;
    }

    /** Mutates {@code unit}'s state of charge and returns its net kW for this tick. Package-visible
     *  for tests, which check the physics without a database. */
    static double applyTick(StorageUnit unit, double frequencyDeviation) {
        if (frequencyDeviation < -DEADBAND_HZ) {
            double energyAvailableKwh = unit.getStateOfChargeKwh();
            double maxEnergyThisTickKwh = unit.getMaxDischargeRateKw() * HOURS_PER_TICK;
            double dischargeKwh = Math.min(energyAvailableKwh, maxEnergyThisTickKwh);

            unit.setStateOfChargeKwh(unit.getStateOfChargeKwh() - dischargeKwh);
            return dischargeKwh / HOURS_PER_TICK;
        }

        if (frequencyDeviation > DEADBAND_HZ) {
            double roomKwh = unit.getCapacityKwh() - unit.getStateOfChargeKwh();
            double maxEnergyThisTickKwh = unit.getMaxChargeRateKw() * HOURS_PER_TICK;
            double chargeKwh = Math.min(roomKwh, maxEnergyThisTickKwh);

            unit.setStateOfChargeKwh(unit.getStateOfChargeKwh() + chargeKwh);
            return -(chargeKwh / HOURS_PER_TICK);
        }

        return 0.0;
    }
}
