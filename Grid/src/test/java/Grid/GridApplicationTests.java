package Grid;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@code autostart=false}: a {@code @SpringBootTest} publishes {@code ApplicationReadyEvent} like
 * any other run, so without this merely loading the context would tick against real Redis and
 * Kafka.
 */
@SpringBootTest(properties = "grid.simulation.autostart=false")
class GridApplicationTests {

    @Test
    void contextLoads() {
    }

}
