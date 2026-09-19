package cz.vsb.reservation.infrastructure.persistence;

import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.model.ReservationState;
import cz.vsb.reservation.domain.port.out.ReservationRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
class ReservationRepositoryAdapter implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;

    ReservationRepositoryAdapter(ReservationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Reservation save(Reservation reservation) {
        ReservationJpaEntity entity = toEntity(reservation);
        ReservationJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Reservation> findConfirmedByResourceAndTimeRange(
            Long resourceId, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findConfirmedOverlapping(resourceId, start, end)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private ReservationJpaEntity toEntity(Reservation reservation) {
        return new ReservationJpaEntity(
                reservation.getId(),
                reservation.getResourceId(),
                reservation.getUserId(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getParticipantCount(),
                reservation.getState().name());
    }

    private Reservation toDomain(ReservationJpaEntity entity) {
        return new Reservation(
                entity.getId(),
                entity.getResourceId(),
                entity.getUserId(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getParticipantCount(),
                ReservationState.valueOf(entity.getState()));
    }
}