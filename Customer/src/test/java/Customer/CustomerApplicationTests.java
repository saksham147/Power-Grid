package Customer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Loads the real context against the real {@code application.yml}.
 *
 * <p>
 * The simulation is switched off with a property on this annotation rather than
 * a test
 * {@code application.yml}. A file of that name in test resources does not merge
 * with the main one --
 * it replaces it on the classpath, so the context would load with no zones and
 * none of the Kafka or
 * Redis tuning, and a broken main configuration would pass this test unnoticed.
 */
@SpringBootTest(properties = "customer.simulation.autostart=false")
class CustomerApplicationTests {

	@Test
	void contextLoads() {
	}

}
