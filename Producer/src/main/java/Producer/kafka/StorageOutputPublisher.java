package Producer.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import Producer.event.StorageOutputEvent;

@Component
public class StorageOutputPublisher {

    public static final String TOPIC = "producer.storage-output";

    private final KafkaTemplate<String, StorageOutputEvent> kafkaTemplate;

    public StorageOutputPublisher(KafkaTemplate<String, StorageOutputEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(StorageOutputEvent event) {
        kafkaTemplate.send(TOPIC, String.valueOf(event.unitId()), event);
    }
}
