package cz.vsb.reservation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, Long> {

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE ReservationJpaEntity r SET r.state = 'EXPIRED' WHERE r.state = 'PENDING_APPROVAL' AND r.startTime <= :now")
    int expirePending(@Param("now") LocalDateTime now);

    List<ReservationJpaEntity> findByStateOrderByStartTimeAsc(String state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReservationJpaEntity r WHERE r.id = :id")
    Optional<ReservationJpaEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT r FROM ReservationJpaEntity r
        WHERE r.resourceId = :resourceId
          AND r.state = 'CONFIRMED'
          AND r.startTime < :end
          AND r.endTime > :start
        """)
    List<ReservationJpaEntity> findConfirmedOverlapping(
            @Param("resourceId") Long resourceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
    List<ReservationJpaEntity> findByUserIdOrderByStartTimeAsc(String userId);

}