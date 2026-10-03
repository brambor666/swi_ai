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

import static org.junit.jupiter.api.Assertions.*;

/** Read-only endpointy pro frontend: seznam učeben a vlastních rezervací. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReadEndpointsApiTest {

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
    private Long firstResourceId;
    private LocalDateTime t10;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM resource");
        firstResourceId = jdbcTemplate.queryForObject(
                "INSERT INTO resource (label, capacity) VALUES ('Učebna A1', 30) RETURNING id", Long.class);
        jdbcTemplate.update("INSERT INTO resource (label, capacity) VALUES ('Sál P1', 120)");
        t10 = LocalDateTime.now(ZoneOffset.UTC).plusDays(1).truncatedTo(ChronoUnit.HOURS);
    }

    private HttpResponse<String> get(String path, String user) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) builder.header("X-User-Id", user);
        return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void insertReservation(String user, LocalDateTime start, String state) {
        jdbcTemplate.update("""
                INSERT INTO reservation (resource_id, user_id, start_time, end_time, participant_count, state)
                VALUES (?, ?, ?, ?, 10, ?)
                """, firstResourceId, user, Timestamp.valueOf(start), Timestamp.valueOf(start.plusHours(1)), state);
    }

    @Test
    void getResources_returnsAllClassroomsOrderedById() throws Exception {
        HttpResponse<String> response = get("/resources", "user-1");

        assertEquals(200, response.statusCode());
        JsonNode json = jsonMapper.readTree(response.body());
        assertEquals(2, json.size());
        assertEquals(firstResourceId, json.get(0).get("id").asLong());
        assertEquals("Učebna A1", json.get(0).get("label").asString());
        assertEquals(30, json.get(0).get("capacity").asInt());
        assertEquals(120, json.get(1).get("capacity").asInt());
    }

    @Test
    void getReservations_returnsOnlyOwnOnes_sortedByStart_withAllStates() throws Exception {
        insertReservation("user-1", t10.plusHours(3), "CANCELLED");
        insertReservation("user-1", t10, "DRAFT");
        insertReservation("user-2", t10, "DRAFT"); // cizí

        HttpResponse<String> response = get("/reservations", "user-1");

        assertEquals(200, response.statusCode());
        JsonNode json = jsonMapper.readTree(response.body());
        assertEquals(2, json.size());
        JsonNode first = json.get(0);
        assertEquals(t10, LocalDateTime.parse(first.get("start").asString()));
        assertEquals(t10.plusHours(1), LocalDateTime.parse(first.get("end").asString()));
        assertEquals(firstResourceId, first.get("resourceId").asLong());
        assertEquals("user-1", first.get("userId").asString());
        assertEquals(10, first.get("participantCount").asInt());
        assertEquals("DRAFT", first.get("state").asString());
        assertEquals("CANCELLED", json.get(1).get("state").asString());
    }

    @Test
    void getReservations_isEmptyForUserWithoutReservations() throws Exception {
        insertReservation("user-1", t10, "DRAFT");

        JsonNode json = jsonMapper.readTree(get("/reservations", "user-2").body());

        assertEquals(0, json.size());
    }

    @Test
    void bothEndpointsRequireIdentity_with401() throws Exception {
        assertEquals(401, get("/resources", null).statusCode());
        assertEquals(401, get("/reservations", null).statusCode());
    }
}