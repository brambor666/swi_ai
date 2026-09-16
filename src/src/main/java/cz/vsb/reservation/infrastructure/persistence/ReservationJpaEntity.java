package cz.vsb.reservation.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation")
public class ReservationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "participant_count", nullable = false)
    private int participantCount;

    @Column(nullable = false)
    private String state;

    protected ReservationJpaEntity() {}

    public ReservationJpaEntity(Long id, Long resourceId, String userId,
                                LocalDateTime startTime, LocalDateTime endTime,
                                int participantCount, String state) {
        this.id = id;
        this.resourceId = resourceId;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.participantCount = participantCount;
        this.state = state;
    }

    public Long getId() { return id; }
    public Long getResourceId() { return resourceId; }
    public String getUserId() { return userId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public int getParticipantCount() { return participantCount; }
    public String getState() { return state; }
}