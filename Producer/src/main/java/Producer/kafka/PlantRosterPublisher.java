package Producer.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import Producer.event.PlantRosterEvent;

@Component
public class PlantRosterPublisher {

    public static final String TOPIC = "producer.plants";

    private final KafkaTemplate<String, PlantRosterEvent> kafkaTemplate;

    public PlantRosterPublisher(KafkaTemplate<String, PlantRosterEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PlantRosterEvent event) {
        kafkaTemplate.send(TOPIC, String.valueOf(event.plantId()), event);
    }
}
