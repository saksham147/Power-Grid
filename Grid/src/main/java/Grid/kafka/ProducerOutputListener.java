package Grid.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import Grid.event.ProducerOutputEvent;
import Grid.simulation.GridStateTracker;

/**
 * Tracks Producer's fleet output as it streams in, so {@link Grid.simulation.GridClockRunner} has
 * a current supply figure to weigh against demand on every tick.
 *
 * <p>
 * A failure here is logged and swallowed rather than thrown: letting an exception escape a
 * {@code @KafkaListener} method retries the same record forever under the default error handling,
 * wedging this topic's partition and silently freezing this plant's contribution to every future
 * automatic control decision.
 */
@Component
public class ProducerOutputListener {

    private static final Logger log = LoggerFactory.getLogger(ProducerOutputListener.class);

    private final GridStateTracker state;

    public ProducerOutputListener(GridStateTracker state) {
        this.state = state;
    }

    @KafkaListener(topics = "producer.output", containerFactory = "producerOutputListenerContainerFactory",
            autoStartup = "${grid.simulation.autostart:true}")
    void onProducerOutput(ProducerOutputEvent event) {
        try {
            state.recordSupply(event.producerId(), event.outputMw());
        } catch (Exception e) {
            log.error("Failed to record output for plant {} (tick {})", event.producerId(), event.tickNumber(), e);
        }
    }
}
