package Producer.generation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Producer.model.PlantType;
import Producer.model.PowerPlant;
import Producer.model.PowerPlantRepository;
import Producer.event.GridTickEvent;
import Producer.event.ProducerOutputEvent;
import Producer.kafka.ProducerOutputPublisher;

/**
 * Turns one grid tick into one output event per active plant.
 *
 * <p>
 * Per tick: one DB read, in-memory strategy dispatch, one batched write-back, N
 * publishes.
 * Nothing else touches Postgres.
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final PowerPlantRepository powerPlantRepository;
    private final ProducerOutputPublisher publisher;
    private final Map<PlantType, GenerationStrategy> strategiesByType;

    public GenerationService(PowerPlantRepository powerPlantRepository,
            ProducerOutputPublisher publisher,
            List<GenerationStrategy> strategies) {
        this.powerPlantRepository = powerPlantRepository;
        this.publisher = publisher;

        // Spring's container is the factory: it discovers every GenerationStrategy bean
        // and this
        // indexes them once, at startup. toUnmodifiableMap throws on a duplicate key,
        // so two
        // strategies claiming the same PlantType fail the context rather than silently
        // shadowing.
        this.strategiesByType = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(GenerationStrategy::getSupportedType, Function.identity()));

        log.info("Registered {} generation strategies: {}", strategiesByType.size(), strategiesByType.keySet());
    }

    /**
     * Runs one tick and returns what it published, so an HTTP caller can be told
     * what the tick
     * actually did without a second read of the table.
     *
     * @return one event per plant that had a strategy, in table order; empty if no
     *         plants are active
     */
    @Transactional
    public List<ProducerOutputEvent> handleTick(GridTickEvent tick) {
        List<PowerPlant> plants = powerPlantRepository.findByActiveTrue();
        List<ProducerOutputEvent> events = new ArrayList<>(plants.size());
        Instant timestamp = Instant.now();

        for (PowerPlant plant : plants) {
            GenerationStrategy strategy = strategiesByType.get(plant.getType());
            if (strategy == null) {
                log.warn("No strategy for plant type {} (plant {}), skipping", plant.getType(), plant.getId());
                continue;
            }

            double outputMw = strategy.calculateOutput(plant, tick);

            // The entities are managed, so this is the write-back: Hibernate dirty-checks
            // and
            // flushes at commit. With jdbc.batch_size and order_updates set that is a
            // single
            // batched round trip, which is why there is no saveAll call here.
            plant.setCurrentOutputMw(outputMw);

            events.add(new ProducerOutputEvent(plant.getId(), tick.tickNumber(), outputMw, timestamp));
        }

        events.forEach(publisher::publish);

        log.debug("Tick {} (deviation {} Hz): published {} output events",
                tick.tickNumber(), tick.frequencyDeviation(), events.size());

        return events;
    }
}
