package cz.vsb.reservation.infrastructure.config;

import cz.vsb.reservation.domain.port.in.ReservationUseCase;
import cz.vsb.reservation.domain.port.out.NotificationPort;
import cz.vsb.reservation.domain.port.out.ReservationRepository;
import cz.vsb.reservation.domain.port.out.ResourceRepository;
import cz.vsb.reservation.domain.service.ReservationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class BeanConfiguration {

    /** BR-01 / BR-04: rozhoduje čas serveru v UTC. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ReservationUseCase reservationUseCase(ReservationRepository reservationRepository,
                                          ResourceRepository resourceRepository,
                                          NotificationPort notificationPort,
                                          Clock clock) {
        return new ReservationService(reservationRepository, resourceRepository, notificationPort, clock);
    }
}