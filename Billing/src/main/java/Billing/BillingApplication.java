package Billing;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} drives {@code Billing.billing.MaintenanceChargeJob}. */
@SpringBootApplication
@EnableScheduling
public class BillingApplication {

	public static void main(String[] args) {
		// Has to happen before the first JDBC connection is opened -- see
		// Producer.ProducerApplication.main for the full explanation. Short version: pgjdbc sends
		// TimeZone.getDefault().getID() during the connection handshake, and a JVM that defaults to
		// Asia/Calcutta (what Windows resolves India to) is rejected by PostgreSQL 18, which dropped
		// that deprecated tzdata alias.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		SpringApplication.run(BillingApplication.class, args);
	}

}
