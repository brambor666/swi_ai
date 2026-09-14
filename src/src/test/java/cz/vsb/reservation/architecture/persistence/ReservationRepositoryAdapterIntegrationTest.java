package cz.vsb.reservation.infrastructure.persistence;

import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.model.ReservationState;
import cz.vsb.reservation.domain.port.out.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrační test podle evidence-and-evolution.md sekce B: reálná PostgreSQL
 * v Dockeru přes Testcontainers, ověřuje mapování na JPA entity i databázový
 * EXCLUDE constraint z ADR-002 (poslední pojistka proti souběhu).
 */
@SpringBootTest
@Testcontainers
class ReservationRepositoryAdapterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ResourceJpaRepository resourceJpaRepository; // package-private, ok v rámci stejného balíčku

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long resourceId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM resource");

        ResourceJpaEntity resource = resourceJpaRepository.save(
                new ResourceJpaEntity(null, "Učebna A1", 20));
        resourceId = resource.getId();
    }

    @Test
    void savedReservationCanBeFoundById() {
        Reservation reservation = Reservation.createDraft(resourceId, "user-1",
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0), 5);

        Reservation saved = reservationRepository.save(reservation);
        Reservation found = reservationRepository.findById(saved.getId()).orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals("user-1", found.getUserId());
        assertEquals(ReservationState.DRAFT, found.getState());
    }

    @Test
    void findConfirmedByResourceAndTimeRange_findsOverlappingConfirmedReservation() {
        Reservation confirmed = Reservation.createDraft(resourceId, "user-1",
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0), 5);
        confirmed.confirm(new cz.vsb.reservation.domain.model.Resource(resourceId, "Učebna A1", 20));
        reservationRepository.save(confirmed);

        List<Reservation> overlapping = reservationRepository.findConfirmedByResourceAndTimeRange(
                resourceId,
                LocalDateTime.of(2026, 10, 1, 10, 30),
                LocalDateTime.of(2026, 10, 1, 10, 45));

        assertEquals(1, overlapping.size());
    }

    @Test
    void findConfirmedByResourceAndTimeRange_ignoresDraftReservations() {
        Reservation draft = Reservation.createDraft(resourceId, "user-1",
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0), 5);
        reservationRepository.save(draft); // zůstává DRAFT, nepotvrzeno

        List<Reservation> overlapping = reservationRepository.findConfirmedByResourceAndTimeRange(
                resourceId,
                LocalDateTime.of(2026, 10, 1, 10, 30),
                LocalDateTime.of(2026, 10, 1, 10, 45));

        assertTrue(overlapping.isEmpty());
    }

    /**
     * ADR-002: i kdyby doménová kontrola v ReservationService nějak selhala
     * (např. race condition mezi dvěma souběžnými požadavky), databázový
     * EXCLUDE USING gist constraint musí zablokovat uložení dvou překrývajících
     * se CONFIRMED rezervací stejné učebny. Test jde záměrně "pod" doménu,
     * přímo přes JDBC, aby ověřil, že se na tuhle pojistku dá spolehnout.
     */
    @Test
    void databaseConstraint_rejectsOverlappingConfirmedReservations() {
        jdbcTemplate.update("""
            INSERT INTO reservation (resource_id, user_id, start_time, end_time, participant_count, state)
            VALUES (?, 'user-1', '2026-10-01 10:00', '2026-10-01 11:00', 5, 'CONFIRMED')
            """, resourceId);

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO reservation (resource_id, user_id, start_time, end_time, participant_count, state)
                    VALUES (?, 'user-2', '2026-10-01 10:30', '2026-10-01 11:30', 5, 'CONFIRMED')
                    """, resourceId));
    }
}