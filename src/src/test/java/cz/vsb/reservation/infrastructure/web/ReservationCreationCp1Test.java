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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** CP1 walking skeleton: POST /reservations → validace → PostgreSQL → 201 + ID → kontrola v databázi. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReservationCreationCp1Test {

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

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM resource");
        resourceId = jdbcTemplate.queryForObject(
                "INSERT INTO resource (label, capacity) VALUES ('Učebna A1', 30) RETURNING id", Long.class);
    }

    /** Server rozhoduje podle skutečných hodin v UTC, proto testovací čas počítáme od "teď". */
    private static LocalDateTime startInOneDay() {
        return LocalDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0);
    }

    private HttpResponse<String> post(String userHeader, Map<String, Object> body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/reservations"))
                .header("Content-Type", "application/json");
        if (userHeader != null) {
            builder.header("X-User-Id", userHeader);
        }
        return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> validBody(LocalDateTime start, int participants) {
        return Map.of(
                "resourceId", resourceId,
                "userId", "user-1",
                "start", start.toString(),
                "end", start.plusHours(1).toString(),
                "participantCount", participants);
    }

    private int reservationCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class);
    }

    @Test
    void post_createsDraft_returns201WithId_andStoresRecordInDatabase() throws Exception {
        LocalDateTime start = startInOneDay();

        HttpResponse<String> response = post("user-1", validBody(start, 20));

        assertEquals(201, response.statusCode());
        JsonNode json = jsonMapper.readTree(response.body());
        long id = json.get("id").asLong();
        assertEquals("DRAFT", json.get("state").asString());

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM reservation WHERE id = ?", id);
        assertEquals(resourceId, ((Number) row.get("resource_id")).longValue());
        assertEquals("user-1", row.get("user_id"));
        assertEquals(start, ((Timestamp) row.get("start_time")).toLocalDateTime());
        assertEquals(start.plusHours(1), ((Timestamp) row.get("end_time")).toLocalDateTime());
        assertEquals(20, row.get("participant_count"));
        assertEquals("DRAFT", row.get("state"));
    }

    @Test
    void post_rejectsOverCapacity_with409_andStoresNothing() throws Exception {
        HttpResponse<String> response = post("user-1", validBody(startInOneDay(), 31)); // kapacita je 30

        assertEquals(409, response.statusCode());
        assertEquals(0, reservationCount());
    }

    @Test
    void post_rejectsLessThanTwoHoursBeforeStart_with409_andStoresNothing() throws Exception {
        LocalDateTime tooSoon = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);

        assertEquals(409, post("user-1", validBody(tooSoon, 10)).statusCode());
        assertEquals(0, reservationCount());
    }

    @Test
    void post_rejectsOwnerDifferentFromAuthenticatedUser_with403() throws Exception {
        assertEquals(403, post("user-2", validBody(startInOneDay(), 10)).statusCode());
        assertEquals(0, reservationCount());
    }

    @Test
    void post_rejectsMissingIdentity_with401() throws Exception {
        assertEquals(401, post(null, validBody(startInOneDay(), 10)).statusCode());
        assertEquals(0, reservationCount());
    }

    @Test
    void post_rejectsUnknownResource_with404() throws Exception {
        Map<String, Object> body = Map.of(
                "resourceId", 999999,
                "userId", "user-1",
                "start", startInOneDay().toString(),
                "end", startInOneDay().plusHours(1).toString(),
                "participantCount", 10);

        assertEquals(404, post("user-1", body).statusCode());
        assertEquals(0, reservationCount());
    }

    @Test
    void post_rejectsInvalidInput_with400_andStoresNothing() throws Exception {
        LocalDateTime start = startInOneDay();

        // start == end
        assertEquals(400, post("user-1", Map.of("resourceId", resourceId, "userId", "user-1",
                "start", start.toString(), "end", start.toString(), "participantCount", 10)).statusCode());
        // počet 0
        assertEquals(400, post("user-1", validBody(start, 0)).statusCode());
        // počet -1
        assertEquals(400, post("user-1", validBody(start, -1)).statusCode());
        // chybějící konec
        assertEquals(400, post("user-1", Map.of("resourceId", resourceId, "userId", "user-1",
                "start", start.toString(), "participantCount", 10)).statusCode());
        // prázdné ID uživatele
        assertEquals(400, post("user-1", Map.of("resourceId", resourceId, "userId", " ",
                "start", start.toString(), "end", start.plusHours(1).toString(), "participantCount", 10)).statusCode());

        assertEquals(0, reservationCount());
    }
}