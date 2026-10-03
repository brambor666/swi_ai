package cz.vsb.reservation.infrastructure.notification;

import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void notifyConfirmed(Reservation reservation) {
        LOG.info("OZNÁMENÍ: rezervace {} uživatele {} byla potvrzena",
                reservation.getId(), reservation.getUserId());
    }

    @Override
    public void notifyCancelled(Reservation reservation) {
        LOG.info("OZNÁMENÍ: rezervace {} uživatele {} byla zrušena",
                reservation.getId(), reservation.getUserId());
    }
}