package cz.vsb.reservation.infrastructure.config;

import cz.vsb.reservation.domain.port.out.ReservationRepository;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.LocalDateTime;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "reservation.expiry.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class ApprovalExpiry {
    private final ReservationRepository repository;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ApprovalExpiry(ReservationRepository repository, Clock clock, PlatformTransactionManager manager) {
        this.repository = repository;
        this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelay = 1000)
    public void expire() {
        transaction.executeWithoutResult(status -> repository.expirePending(LocalDateTime.now(clock)));
    }
}
