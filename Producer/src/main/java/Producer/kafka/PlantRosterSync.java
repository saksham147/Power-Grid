package Producer.kafka;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import Producer.event.PlantRosterEvent;
import Producer.model.PowerPlant;
import Producer.model.PowerPlantRepository;

/**
 * Re-announces every plant on {@code producer.plants}, at startup and then periodically, so a
 * consumer's mirror of the roster (Billing's, for maintenance and running-cost figures) is always
 * rebuildable from the topic alone.
 *
 * <p>
 * {@link PlantRosterPublisher} is only called when a plant is created, re-rated, toggled or
 * deleted, so on its own the topic holds nothing for a plant that has not changed since the
 * roster existed -- a plant built before the topic did, or one whose event has aged out of
 * Kafka's retention, would be invisible to Billing forever (no upkeep charged, no running cost
 * shown) and nothing would ever say so. Publishing the full roster on a schedule closes that gap
 * from the source: the events are idempotent to a mirror (it just overwrites the entry), and a
 * handful of tiny messages every few minutes costs nothing.
 *
 * <p>
 * Runs on the shared single-thread scheduler {@code SimulationConfig} provides -- one more small
 * periodic job, not worth a scheduler of its own -- and starts on {@code ApplicationReadyEvent}
 * with an immediate first run, so a fresh start announces the roster straight away rather than
 * after a full interval.
 */
@Component
public class PlantRosterSync {

    private static final Logger log = LoggerFactory.getLogger(PlantRosterSync.class);

    private final PowerPlantRepository plants;
    private final PlantRosterPublisher publisher;
    private final TaskScheduler scheduler;
    private final boolean enabled;
    private final Duration interval;

    public PlantRosterSync(PowerPlantRepository plants, PlantRosterPublisher publisher,
            @Qualifier("simulationTaskScheduler") TaskScheduler scheduler,
            @Value("${producer.roster-sync.enabled:true}") boolean enabled,
            @Value("${producer.roster-sync.interval:PT10M}") Duration interval) {
        this.plants = plants;
        this.publisher = publisher;
        this.scheduler = scheduler;
        this.enabled = enabled;
        this.interval = interval;
    }

    @EventListener(ApplicationReadyEvent.class)
    void startOnBoot() {
        if (!enabled) {
            log.info("Plant roster sync is disabled");
            return;
        }
        scheduler.scheduleWithFixedDelay(this::runSafely, interval);
        log.info("Plant roster will be re-announced on {} every {}", PlantRosterPublisher.TOPIC, interval);
    }

    /** @return how many plants were announced */
    int syncAll() {
        List<PowerPlant> all = plants.findAll();
        Instant now = Instant.now();
        for (PowerPlant plant : all) {
            publisher.publish(new PlantRosterEvent(
                    plant.getId(), plant.getType(), plant.getCapacityMw(), plant.isActive(), false, now));
        }
        return all.size();
    }

    private void runSafely() {
        try {
            int announced = syncAll();
            log.debug("Re-announced {} plant(s) on {}", announced, PlantRosterPublisher.TOPIC);
        } catch (Exception e) {
            // Swallowed for the same reason every other scheduled job here does this: an
            // exception that escapes cancels every future run on this scheduler.
            log.error("Plant roster sync failed; will retry next interval", e);
        }
    }
}
