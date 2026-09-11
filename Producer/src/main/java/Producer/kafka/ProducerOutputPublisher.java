package Producer.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import Producer.event.ProducerOutputEvent;

@Component
public class ProducerOutputPublisher {

    public static final String TOPIC = "producer.output";

    private final KafkaTemplate<String, ProducerOutputEvent> kafkaTemplate;

    public ProducerOutputPublisher(KafkaTemplate<String, ProducerOutputEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ProducerOutputEvent event) {
        // Keying by plant id is what pins one plant's history to one partition, and
        // therefore what
        // makes its output sequence ordered for every downstream consumer.
        kafkaTemplate.send(TOPIC, String.valueOf(event.producerId()), event);
    }
}
