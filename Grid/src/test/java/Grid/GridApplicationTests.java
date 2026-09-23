package Grid;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@code autostart=false}: a {@code @SpringBootTest} publishes {@code ApplicationReadyEvent} like
 * any other run, so without this merely loading the context would tick against real Redis and
 * Kafka. {@code resume-attempts=1}: the boot hook still reads the saved clock before it looks at
 * autostart, and with no Redis in a unit-test run that read would otherwise sit through every
 * retry of the real boot-time backoff.
 */
@SpringBootTest(properties = { "grid.simulation.autostart=false", "grid.clock.resume-attempts=1" })
class GridApplicationTests {

    @Test
    void contextLoads() {
    }

}
