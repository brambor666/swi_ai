package cz.vsb.reservation.infrastructure.config;

import cz.vsb.reservation.domain.port.in.ReservationUseCase;
import cz.vsb.reservation.domain.port.out.NotificationPort;
import cz.vsb.reservation.domain.port.out.ReservationRepository;
import cz.vsb.reservation.domain.port.out.ResourceRepository;
import cz.vsb.reservation.domain.service.ReservationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import cz.vsb.reservation.domain.model.Reservation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@org.springframework.scheduling.annotation.EnableScheduling
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
                                          Clock clock, PlatformTransactionManager transactionManager,
                                          @org.springframework.beans.factory.annotation.Value("${reservation.approvers:admin}") String approverIds) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        // Oznámení se předává až po úspěšném commitu, nikdy po odmítnutém zápisu.
        NotificationPort afterCommitNotifications = new NotificationPort() {
            private void schedule(Runnable action) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() {
                        try { action.run(); }
                        catch (RuntimeException e) {
                            System.getLogger(BeanConfiguration.class.getName()).log(
                                    System.Logger.Level.WARNING, "Oznámení zahozeno po commitu", e);
                        }
                    }
                });
            }
            @Override public void notifyConfirmed(Reservation reservation) {
                schedule(() -> notificationPort.notifyConfirmed(reservation));
            }
            @Override public void notifyCancelled(Reservation reservation) {
                schedule(() -> notificationPort.notifyCancelled(reservation));
            }
        };
        return new ReservationService(reservationRepository, resourceRepository, afterCommitNotifications, clock,
                java.util.Arrays.stream(approverIds.split(",")).map(String::trim).filter(id -> !id.isEmpty()).collect(java.util.stream.Collectors.toSet())) {
            private void refreshExpiry() {
                transaction.executeWithoutResult(status -> reservationRepository.expirePending(java.time.LocalDateTime.now(clock)));
            }
            @Override public Reservation confirmReservation(Long id, String user) {
                refreshExpiry();
                return transaction.execute(status -> super.confirmReservation(id, user));
            }
            @Override public Reservation cancelReservation(Long id, String user) {
                refreshExpiry();
                return transaction.execute(status -> super.cancelReservation(id, user));
            }
            @Override public Reservation approveReservation(Long id, String user) {
                refreshExpiry();
                return transaction.execute(status -> super.approveReservation(id, user));
            }
            @Override public Reservation rejectReservation(Long id, String user) {
                refreshExpiry();
                return transaction.execute(status -> super.rejectReservation(id, user));
            }
            @Override public java.util.List<Reservation> listReservations(String user) {
                refreshExpiry();
                return super.listReservations(user);
            }
            @Override public java.util.List<Reservation> listPendingApprovals(String user) {
                refreshExpiry();
                return super.listPendingApprovals(user);
            }
        };
    }
}