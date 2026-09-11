package Producer;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProducerApplication {

	public static void main(String[] args) {
		// Has to happen before the first JDBC connection is opened.
		//
		// pgjdbc sends TimeZone.getDefault().getID() as the connection's TimeZone startup
		// parameter, and PostgreSQL 18 dropped the deprecated tzdata aliases. A JVM that
		// defaults to Asia/Calcutta -- which is what Windows resolves India to -- is therefore
		// rejected during the handshake: FATAL: invalid value for parameter "TimeZone":
		// "Asia/Calcutta". Not a connection failure that retries fix; the server refuses.
		//
		// Pinning UTC is what we want anyway: every timestamp published here is an Instant,
		// the simulation clock is the tick number rather than wall-clock time, and a fixed
		// default keeps behaviour identical across machines. Surefire sets the same default
		// for tests, which do not come through main().
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		SpringApplication.run(ProducerApplication.class, args);
	}

}
