package cz.vsb.reservation.infrastructure.persistence;

import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.model.ReservationState;
import cz.vsb.reservation.domain.model.Resource;
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

    private static final String USER = "user-1";
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 1, 11, 0);
    // Čas "serveru" předáváme explicitně, takže test nezávisí na skutečných hodinách.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 7, 0);

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
    private Resource domainResource;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM resource");

        ResourceJpaEntity resource = resourceJpaRepository.save(
                new ResourceJpaEntity(null, "Učebna A1", 20));
        resourceId = resource.getId();
        domainResource = new Resource(resourceId, "Učebna A1", 20);
    }

    private Reservation newDraft(int participants) {
        return Reservation.createDraft(domainResource, USER, USER, START, END, participants, NOW);
    }

    @Test
    void savedReservationCanBeFoundById() {
        Reservation saved = reservationRepository.save(newDraft(5));
        Reservation found = reservationRepository.findById(saved.getId()).orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals(resourceId, found.getResourceId());
        assertEquals(USER, found.getUserId());
        assertEquals(START, found.getStartTime());
        assertEquals(END, found.getEndTime());
        assertEquals(5, found.getParticipantCount());
        assertEquals(ReservationState.DRAFT, found.getState());
    }

    @Test
    void findConfirmedByResourceAndTimeRange_findsOverlappingConfirmedReservation() {
        Reservation confirmed = newDraft(5);
        confirmed.confirm(domainResource, USER, NOW);
        reservationRepository.save(confirmed);

        List<Reservation> overlapping = reservationRepository.findConfirmedByResourceAndTimeRange(
                resourceId,
                LocalDateTime.of(2026, 10, 1, 10, 30),
                LocalDateTime.of(2026, 10, 1, 10, 45));

        assertEquals(1, overlapping.size());
        assertEquals(ReservationState.CONFIRMED, overlapping.get(0).getState());
    }

    @Test
    void findConfirmedByResourceAndTimeRange_ignoresDraftReservations() {
        reservationRepository.save(newDraft(5)); // zůstává DRAFT, nepotvrzeno

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

    /** Navazující intervaly se nepřekrývají (BR-01), constraint je tedy musí propustit. */
    @Test
    void databaseConstraint_allowsBackToBackConfirmedReservations() {
        jdbcTemplate.update("""
            INSERT INTO reservation (resource_id, user_id, start_time, end_time, participant_count, state)
            VALUES (?, 'user-1', '2026-10-01 10:00', '2026-10-01 11:00', 5, 'CONFIRMED')
            """, resourceId);

        assertDoesNotThrow(() -> jdbcTemplate.update("""
            INSERT INTO reservation (resource_id, user_id, start_time, end_time, participant_count, state)
            VALUES (?, 'user-2', '2026-10-01 11:00', '2026-10-01 12:00', 5, 'CONFIRMED')
            """, resourceId));
    }
}