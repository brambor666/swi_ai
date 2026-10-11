package cz.vsb.reservation.infrastructure.config;

import cz.vsb.reservation.domain.port.in.ReservationUseCase;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "reservation.expiry.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class ApprovalExpiry {
    private final ReservationUseCase useCase;

    public ApprovalExpiry(ReservationUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelay = 1000)
    public void expire() {
        useCase.expirePendingApprovals();
    }
}
