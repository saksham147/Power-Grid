package Grid.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import Grid.event.GridTickEvent;

/**
 * Publishes each tick to {@code grid.tick}, the stream Producer and Customer react to instead of
 * ticking themselves.
 */
@Component
public class GridTickPublisher {

    public static final String TOPIC = "grid.tick";

    private final KafkaTemplate<String, GridTickEvent> kafkaTemplate;

    public GridTickPublisher(KafkaTemplate<String, GridTickEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(GridTickEvent event) {
        // Null key: every consumer must see every tick in order, so there is nothing to
        // partition by -- see the topic declaration for why it has exactly one partition.
        kafkaTemplate.send(TOPIC, null, event);
    }
}
