package cz.vsb.reservation.infrastructure.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Potvrzení, zrušení a dostupnost přes HTTP proti reálné PostgreSQL. */
@org.springframework.test.context.TestPropertySource(properties = "reservation.expiry.enabled=false")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReservationLifecycleApiTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient http = HttpClient.newHttpClient();
    private Long resourceId;
    private LocalDateTime t10; // "10:00" zítřejšího dne, od něj se odvíjí všechny časy

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM resource");
        resourceId = jdbcTemplate.queryForObject(
                "INSERT INTO resource (label, capacity) VALUES ('Učebna A1', 30) RETURNING id", Long.class);
        t10 = LocalDateTime.now(ZoneOffset.UTC).plusDays(1).truncatedTo(ChronoUnit.HOURS);
    }

    // ---------- pomocné metody ----------

    private HttpResponse<String> send(String method, String path, String user, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) builder.header("X-User-Id", user);
        if (body != null) builder.header("Content-Type", "application/json");
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return http.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private long createDraft(String user, LocalDateTime start, int participants) throws Exception {
        String body = jsonMapper.writeValueAsString(Map.of(
                "resourceId", resourceId, "userId", user,
                "start", start.toString(), "end", start.plusHours(1).toString(),
                "participantCount", participants));
        HttpResponse<String> response = send("POST", "/reservations", user, body);
        assertEquals(201, response.statusCode());
        return jsonMapper.readTree(response.body()).get("id").asLong();
    }

    private HttpResponse<String> confirm(long id, String user) throws Exception {
        return send("POST", "/reservations/" + id + "/confirm", user, null);
    }

    private HttpResponse<String> cancel(long id, String user) throws Exception {
        return send("POST", "/reservations/" + id + "/cancel", user, null);
    }

    private boolean available(String user, LocalDateTime start, LocalDateTime end) throws Exception {
        HttpResponse<String> response = send("GET",
                "/resources/" + resourceId + "/availability?start=" + start + "&end=" + end, user, null);
        assertEquals(200, response.statusCode());
        return jsonMapper.readTree(response.body()).get("available").asBoolean();
    }

    private String stateInDb(long id) {
        return jdbcTemplate.queryForObject("SELECT state FROM reservation WHERE id = ?", String.class, id);
    }

    /** Připraví návrh pro test časových pravidel. */
    private long insertDraftStartingInOneHour(String user) {
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
        return jdbcTemplate.queryForObject("""
                INSERT INTO reservation (resource_id, user_id, start_time, end_time, participant_count, state)
                VALUES (?, ?, ?, ?, 10, 'DRAFT') RETURNING id
                """, Long.class, resourceId, user,
                Timestamp.valueOf(start), Timestamp.valueOf(start.plusHours(1)));
    }

    // ---------- potvrzení ----------

    @Test
    void confirm_movesDraftToConfirmed_returns200_andPersistsState() throws Exception {
        long id = createDraft("user-1", t10, 20);

        HttpResponse<String> response = confirm(id, "user-1");

        assertEquals(200, response.statusCode());
        JsonNode json = jsonMapper.readTree(response.body());
        assertEquals(id, json.get("id").asLong());
        assertEquals("CONFIRMED", json.get("state").asString());
        assertEquals("CONFIRMED", stateInDb(id));
    }

    @Test
    void confirm_rejectsOverlap_with409_andKeepsDraft() throws Exception {
        confirm(createDraft("user-1", t10, 10), "user-1");
        long conflicting = createDraft("user-2", t10.plusMinutes(30), 10); // návrh kolizi nevadí

        HttpResponse<String> response = confirm(conflicting, "user-2");

        assertEquals(409, response.statusCode());
        assertEquals("BUSINESS_RULE_VIOLATION", jsonMapper.readTree(response.body()).get("code").asString());
        assertEquals("DRAFT", stateInDb(conflicting));
    }

    @Test
    void confirm_allowsBackToBackIntervals() throws Exception {
        confirm(createDraft("user-1", t10, 10), "user-1");
        long next = createDraft("user-2", t10.plusHours(1), 10);

        assertEquals(200, confirm(next, "user-2").statusCode());
    }

    @Test
    void confirm_rejectsForeignUser_with403_unknownId_with404_missingIdentity_with401() throws Exception {
        long id = createDraft("user-1", t10, 10);

        assertEquals(403, confirm(id, "user-2").statusCode());
        assertEquals(404, confirm(999999, "user-1").statusCode());
        assertEquals(401, confirm(id, null).statusCode());
        assertEquals("DRAFT", stateInDb(id));
    }

    @Test
    void confirm_rejectsAlreadyConfirmed_with409() throws Exception {
        long id = createDraft("user-1", t10, 10);
        confirm(id, "user-1");

        HttpResponse<String> response = confirm(id, "user-1");

        assertEquals(409, response.statusCode());
        assertEquals("INVALID_STATE", jsonMapper.readTree(response.body()).get("code").asString());
    }

    @Test
    void confirm_allowsLessThanTwoHoursBeforeStart() throws Exception {
        long id = insertDraftStartingInOneHour("user-1");

        assertEquals(200, confirm(id, "user-1").statusCode());
        assertEquals("CONFIRMED", stateInDb(id));
    }

    @Test
    void confirm_rejectsNonNumericId_with400() throws Exception {
        assertEquals(400, send("POST", "/reservations/abc/confirm", "user-1", null).statusCode());
    }

    // ---------- zrušení ----------

    @Test
    void cancel_confirmedReservation_keepsRecord_andReleasesInterval() throws Exception {
        long id = createDraft("user-1", t10, 10);
        confirm(id, "user-1");
        assertFalse(available("user-2", t10, t10.plusHours(1)));

        HttpResponse<String> response = cancel(id, "user-1");

        assertEquals(200, response.statusCode());
        assertEquals("CANCELLED", jsonMapper.readTree(response.body()).get("state").asString());
        assertEquals("CANCELLED", stateInDb(id));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class)); // záznam zůstal
        assertTrue(available("user-2", t10, t10.plusHours(1)));
    }

    @Test
    void cancel_draft_isAllowed() throws Exception {
        long id = createDraft("user-1", t10, 10);

        assertEquals(200, cancel(id, "user-1").statusCode());
        assertEquals("CANCELLED", stateInDb(id));
    }

    @Test
    void cancel_rejectsRepeatedCancellation_with409() throws Exception {
        long id = createDraft("user-1", t10, 10);
        cancel(id, "user-1");

        assertEquals(409, cancel(id, "user-1").statusCode());
    }

    @Test
    void cancel_rejectsForeignUser_with403_unknownId_with404() throws Exception {
        long id = createDraft("user-1", t10, 10);

        assertEquals(403, cancel(id, "user-2").statusCode());
        assertEquals(404, cancel(999999, "user-1").statusCode());
        assertEquals("DRAFT", stateInDb(id));
    }

    @Test
    void cancel_rejectsLessThanTwoHoursBeforeStart_with409() throws Exception {
        long id = insertDraftStartingInOneHour("user-1");
        assertEquals(200, confirm(id, "user-1").statusCode());

        assertEquals(409, cancel(id, "user-1").statusCode());
        assertEquals("CONFIRMED", stateInDb(id));
    }

    // ---------- dostupnost ----------

    @Test
    void availability_followsSpecificationExamples() throws Exception {
        confirm(createDraft("user-1", t10, 10), "user-1"); // [10:00, 11:00)

        assertTrue(available("user-2", t10.minusHours(1), t10));                    // [09:00, 10:00)
        assertFalse(available("user-2", t10.plusMinutes(30), t10.plusMinutes(90))); // [10:30, 11:30)
        assertTrue(available("user-2", t10.plusHours(1), t10.plusHours(2)));        // [11:00, 12:00)
        assertFalse(available("user-2", t10, t10.plusHours(1)));                    // totožný interval
        assertFalse(available("user-2", t10.plusMinutes(15), t10.plusMinutes(45))); // uvnitř
    }

    @Test
    void availability_ignoresDraftReservations_andChangesNothing() throws Exception {
        createDraft("user-1", t10, 10); // jen návrh, učebnu neblokuje

        assertTrue(available("user-2", t10, t10.plusHours(1)));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class));
    }

    @Test
    void availability_rejectsInvalidRequests() throws Exception {
        String base = "/resources/" + resourceId + "/availability";

        assertEquals(401, send("GET", base + "?start=" + t10 + "&end=" + t10.plusHours(1), null, null).statusCode());
        assertEquals(404, send("GET", "/resources/999999/availability?start=" + t10 + "&end=" + t10.plusHours(1),
                "user-1", null).statusCode());
        assertEquals(400, send("GET", base + "?start=" + t10 + "&end=" + t10, "user-1", null).statusCode()); // start == end
        assertEquals(400, send("GET", base + "?start=" + t10, "user-1", null).statusCode());                // chybí end
        assertEquals(400, send("GET", base + "?start=zitra&end=pozitri", "user-1", null).statusCode());     // špatný formát
    }
    private List<Integer> race(Callable<HttpResponse<String>> first,
                               Callable<HttpResponse<String>> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var a = executor.submit(() -> { ready.countDown(); start.await(); return first.call().statusCode(); });
            var b = executor.submit(() -> { ready.countDown(); start.await(); return second.call().statusCode(); });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentConfirmAndCancel_cannotRestoreCancelledReservation() throws Exception {
        long id = createDraft("user-1", t10, 10);
        var statuses = race(() -> confirm(id, "user-1"), () -> cancel(id, "user-1"));
        assertEquals(200, statuses.get(1));
        assertTrue(statuses.get(0) == 200 || statuses.get(0) == 409);
        assertEquals("CANCELLED", stateInDb(id));
        assertTrue(available("user-2", t10, t10.plusHours(1)));
    }

    @Test
    void concurrentRepeatedConfirm_onlyOneSucceeds() throws Exception {
        long id = createDraft("user-1", t10, 10);
        var statuses = race(() -> confirm(id, "user-1"), () -> confirm(id, "user-1"));
        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        assertEquals("CONFIRMED", stateInDb(id));
    }

    @Test
    void concurrentRepeatedCancel_onlyOneSucceeds() throws Exception {
        long id = createDraft("user-1", t10, 10);
        var statuses = race(() -> cancel(id, "user-1"), () -> cancel(id, "user-1"));
        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        assertEquals("CANCELLED", stateInDb(id));
    }

    @Test
    void concurrentConflictingConfirmations_onlyOneAllocation() throws Exception {
        long a = createDraft("user-1", t10, 10);
        long b = createDraft("user-2", t10.plusMinutes(30), 10);
        var statuses = race(() -> confirm(a, "user-1"), () -> confirm(b, "user-2"));
        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE state = 'CONFIRMED'", Integer.class));
    }

    @Test
    void concurrentConfirmAndCancel_lessThanTwoHoursBeforeStart_respectsOrder() throws Exception {
        long id = createDraft("user-1", LocalDateTime.now(ZoneOffset.UTC).plusHours(1), 10);
        var statuses = race(() -> confirm(id, "user-1"), () -> cancel(id, "user-1"));
        if ("CANCELLED".equals(stateInDb(id))) {
            assertEquals(List.of(409, 200), statuses);
        } else {
            assertEquals("CONFIRMED", stateInDb(id));
            assertEquals(List.of(200, 409), statuses);
        }
    }

    @Test
    void cancel_pastDraft_hasNoTimeLimit() throws Exception {
        long id = insertDraftStartingInOneHour("user-1");
        jdbcTemplate.update("UPDATE reservation SET start_time = ?, end_time = ? WHERE id = ?",
                Timestamp.valueOf(t10.minusDays(3)), Timestamp.valueOf(t10.minusDays(3).plusHours(1)), id);
        assertEquals(200, cancel(id, "user-1").statusCode());
        assertEquals("CANCELLED", stateInDb(id));
    }

    @Test
    void confirmWaitingForCancel_readsCommittedCancelledState() throws Exception {
        long id = createDraft("user-1", t10, 10);
        try (var connection = jdbcTemplate.getDataSource().getConnection();
             var executor = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try {
                // Necommitnuté zrušení drží řádek: Confirm musí počkat a znovu načíst stav.
                try (var statement = connection.prepareStatement(
                        "UPDATE reservation SET state = 'CANCELLED' WHERE id = ?")) {
                    statement.setLong(1, id);
                    statement.executeUpdate();
                }
                var confirmation = executor.submit(() -> confirm(id, "user-1"));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                            "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE '%reservation%')",
                            Boolean.class));
                    if (waiting) break;
                    Thread.sleep(25);
                }
                connection.commit();
                assertTrue(waiting, "Confirm musí skutečně čekat na databázový zámek");
                var response = confirmation.get(10, TimeUnit.SECONDS);
                assertEquals(409, response.statusCode());
                assertEquals("INVALID_STATE", jsonMapper.readTree(response.body()).get("code").asString());
                assertEquals("CANCELLED", stateInDb(id));
            } finally {
                connection.rollback();
            }
        }
    }

}