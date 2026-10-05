package cz.vsb.reservation.reservation_system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.TestPropertySource(properties = "reservation.expiry.enabled=false")
@SpringBootTest
class ReservationSystemApplicationTests {

	@Test
	void contextLoads() {
	}

}
