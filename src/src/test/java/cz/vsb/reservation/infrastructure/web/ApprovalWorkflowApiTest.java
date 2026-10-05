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
@org.springframework.context.annotation.Import(ApprovalWorkflowApiTest.TimeConfiguration.class)
@Testcontainers
class ApprovalWorkflowApiTest {

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

    @Autowired
    private MutableClock testClock;

    @BeforeEach
    void setUp() {
        testClock.time = java.time.Instant.parse("2026-12-01T07:00:00Z");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM resource");
        resourceId = jdbcTemplate.queryForObject(
                "INSERT INTO resource (label, capacity) VALUES ('Učebna A1', 30) RETURNING id", Long.class);
        jdbcTemplate.update("UPDATE resource SET requires_approval = TRUE WHERE id = ?", resourceId);
        t10 = LocalDateTime.of(2026, 12, 1, 10, 0);
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


    static class MutableClock extends java.time.Clock {
        volatile java.time.Instant time = java.time.Instant.parse("2026-12-01T07:00:00Z");
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId zone) { return java.time.Clock.fixed(time, zone); }
        public java.time.Instant instant() { return time; }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TimeConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        MutableClock testClock() { return new MutableClock(); }
    }

    private long pending() throws Exception {
        long id = createDraft("user-1", t10, 10);
        var result = confirm(id, "user-1");
        assertEquals(200, result.statusCode());
        assertEquals("PENDING_APPROVAL", stateInDb(id));
        return id;
    }

    private HttpResponse<String> decide(long id, String operation, String user) throws Exception {
        return send("POST", "/reservations/" + id + "/" + operation, user, null);
    }

    @Test
    void delayedApproval_doesNotBlock_untilApproved() throws Exception {
        long id = pending();
        assertTrue(available("user-2", t10, t10.plusHours(1)));
        testClock.time = java.time.Instant.parse("2026-12-01T09:59:59Z");
        assertEquals(200, decide(id, "approve", "admin").statusCode());
        assertEquals("CONFIRMED", stateInDb(id));
        assertFalse(available("user-2", t10, t10.plusHours(1)));
        assertEquals(409, decide(id, "approve", "admin").statusCode());
    }

    @Test
    void unauthorizedDecisions_changeNothing() throws Exception {
        long id = pending();
        assertEquals(403, decide(id, "approve", "user-1").statusCode());
        assertEquals(403, decide(id, "reject", "user-2").statusCode());
        assertEquals(401, decide(id, "approve", null).statusCode());
        assertEquals(403, send("GET", "/reservations/pending-approvals", "user-1", null).statusCode());
        assertEquals("PENDING_APPROVAL", stateInDb(id));
        assertEquals(200, send("GET", "/reservations/pending-approvals", "admin", null).statusCode());
    }

    @Test
    void rejection_isTerminal_andDoesNotBlock() throws Exception {
        long id = pending();
        assertEquals(200, decide(id, "reject", "admin").statusCode());
        assertEquals("REJECTED", stateInDb(id));
        assertTrue(available("user-2", t10, t10.plusHours(1)));
        assertEquals(409, decide(id, "approve", "admin").statusCode());
        assertEquals(409, decide(id, "reject", "admin").statusCode());
        assertEquals(409, cancel(id, "user-1").statusCode());
    }

    @Test
    void exactStart_expiresAndCannotBeApprovedOrCancelled() throws Exception {
        long id = pending();
        testClock.time = t10.toInstant(ZoneOffset.UTC);
        assertEquals(409, decide(id, "approve", "admin").statusCode());
        assertEquals("EXPIRED", stateInDb(id));
        assertEquals(409, decide(id, "reject", "admin").statusCode());
        assertEquals(409, cancel(id, "user-1").statusCode());
        assertTrue(available("user-2", t10, t10.plusHours(1)));
    }

    @Test
    void cancelledPending_cannotBeApproved() throws Exception {
        long id = pending();
        testClock.time = java.time.Instant.parse("2026-12-01T09:59:59Z");
        assertEquals(403, cancel(id, "user-2").statusCode());
        assertEquals(200, cancel(id, "user-1").statusCode());
        assertEquals("CANCELLED", stateInDb(id));
        assertEquals(409, decide(id, "approve", "admin").statusCode());
        assertTrue(available("user-2", t10, t10.plusHours(1)));
    }

    @Test
    void competingRequests_recheckAvailabilityAtApproval() throws Exception {
        long a = pending();
        long b = pending();
        assertEquals(200, decide(a, "approve", "admin").statusCode());
        assertEquals(409, decide(b, "approve", "admin").statusCode());
        assertEquals("PENDING_APPROVAL", stateInDb(b));
        assertEquals(200, decide(b, "reject", "admin").statusCode());
    }

    @Test
    void approvalRechecksCapacity_andDraftCannotBeApproved() throws Exception {
        long draft = createDraft("user-1", t10, 10);
        assertEquals(409, decide(draft, "approve", "admin").statusCode());
        assertEquals(409, decide(draft, "reject", "admin").statusCode());
        long id = pending();
        jdbcTemplate.update("UPDATE resource SET capacity = 5 WHERE id = ?", resourceId);
        assertEquals(409, decide(id, "approve", "admin").statusCode());
        assertEquals("PENDING_APPROVAL", stateInDb(id));
        assertEquals(404, decide(999999, "approve", "admin").statusCode());
    }

    @Test
    void ordinaryRoom_remainsAutomaticallyConfirmed() throws Exception {
        jdbcTemplate.update("UPDATE resource SET requires_approval = FALSE WHERE id = ?", resourceId);
        long id = createDraft("user-1", t10, 10);
        assertEquals(200, confirm(id, "user-1").statusCode());
        assertEquals("CONFIRMED", stateInDb(id));
    }

    @Test
    void pendingSurvivesDatabaseReload_andExpiresOnRead() throws Exception {
        long id = pending();
        var response = send("GET", "/reservations", "user-1", null);
        assertTrue(response.body().contains("PENDING_APPROVAL"));
        testClock.time = t10.toInstant(ZoneOffset.UTC);
        response = send("GET", "/reservations", "user-1", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("EXPIRED"));
        assertEquals("EXPIRED", stateInDb(id));
    }

    @Test
    void concurrentApprovalAndCancellation_respectsStateAndTime() throws Exception {
        long id = pending();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var a = executor.submit(() -> { start.await(); return decide(id, "approve", "admin").statusCode(); });
            var b = executor.submit(() -> { start.await(); return cancel(id, "user-1").statusCode(); });
            start.countDown();
            int approval = a.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(200, b.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(approval == 200 || approval == 409);
            assertEquals("CANCELLED", stateInDb(id));
        }
    }
    @Autowired private cz.vsb.reservation.domain.port.out.ReservationRepository repository;
    @Autowired private org.springframework.transaction.PlatformTransactionManager manager;

    @Test
    void expiryScanner_materializesExpiredWithoutUserAction() throws Exception {
        long id = pending();
        testClock.time = t10.toInstant(ZoneOffset.UTC);
        new cz.vsb.reservation.infrastructure.config.ApprovalExpiry(repository, testClock, manager).expire();
        assertEquals("EXPIRED", stateInDb(id));
    }

    private java.util.List<Integer> decisions(long a, String first, long b, String second) throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var x = executor.submit(() -> { start.await(); return decide(a, first, "admin").statusCode(); });
            var y = executor.submit(() -> { start.await(); return decide(b, second, "admin").statusCode(); });
            start.countDown();
            return java.util.List.of(x.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    y.get(20, java.util.concurrent.TimeUnit.SECONDS)).stream().sorted().toList();
        }
    }

    @Test
    void concurrentRepeatedApprove_onlyOneSucceeds() throws Exception {
        long id = pending();
        assertEquals(java.util.List.of(200, 409), decisions(id, "approve", id, "approve"));
        assertEquals("CONFIRMED", stateInDb(id));
    }

    @Test
    void concurrentApproveAndReject_onlyOneDecision() throws Exception {
        long id = pending();
        assertEquals(java.util.List.of(200, 409), decisions(id, "approve", id, "reject"));
        assertTrue(java.util.Set.of("CONFIRMED", "REJECTED").contains(stateInDb(id)));
    }

    @Test
    void concurrentConflictingApprovals_onlyOneAllocation() throws Exception {
        long a = pending();
        long b = pending();
        assertEquals(java.util.List.of(200, 409), decisions(a, "approve", b, "approve"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation WHERE state = 'CONFIRMED'", Integer.class));
    }

    @Test
    void changingRoomPolicy_cannotBypassApprovalOfPendingRequest() throws Exception {
        long id = pending();
        jdbcTemplate.update("UPDATE resource SET requires_approval = FALSE WHERE id = ?", resourceId);
        assertEquals(409, confirm(id, "user-1").statusCode());
        assertEquals("PENDING_APPROVAL", stateInDb(id));
        assertEquals(200, decide(id, "approve", "admin").statusCode());
    }

}
