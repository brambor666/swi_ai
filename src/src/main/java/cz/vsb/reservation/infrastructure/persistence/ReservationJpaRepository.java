package cz.vsb.reservation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, Long> {

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
}