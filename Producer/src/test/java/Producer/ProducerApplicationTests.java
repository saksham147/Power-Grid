package Producer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Both background loops are off. A {@code @SpringBootTest} publishes
 * {@code ApplicationReadyEvent}
 * like any real run, so without these properties loading the context started the
 * simulation and
 * wrote real rows to Postgres and real events to Kafka every time the tests ran.
 */
@SpringBootTest(properties = {
        "producer.simulation.autostart=false",
        "producer.history.rollup-enabled=false"
})
class ProducerApplicationTests {

	@Test
	void contextLoads() {
	}

}
