package Billing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@code autostart=false}: a {@code @SpringBootTest} publishes {@code ApplicationReadyEvent} like
 * any other run, so without this merely loading the context would consume against real Kafka.
 */
@SpringBootTest(properties = "billing.autostart=false")
class BillingApplicationTests {

	@Test
	void contextLoads() {
	}

}
