package edu.vit.hcms.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.vit.hcms.web.HcmsApplication;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("API Test: Admin Triage Endpoint and Queue Ordering")
class AdminTriageIT {

    private static Javalin app;
    private static int port;
    private static HttpClient client;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static String adminToken;

    @BeforeAll
    static void setUp() throws Exception {
        app = HcmsApplication.createServer(0);
        port = app.port();
        client = HttpClient.newHttpClient();

        // Login as Admin
        String loginPayload = "{\"email\":\"admin@vit.ac.in\",\"password\":\"password123\"}";
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build();

        HttpResponse<String> loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginRes.statusCode(), "Admin login should succeed");

        JsonNode root = mapper.readTree(loginRes.body());
        adminToken = root.path("token").asText();
        assertNotNull(adminToken, "Admin token must not be null");
    }

    @AfterAll
    static void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName("GET /api/v1/admin/triage returns counters and complaints ordered by triage priority")
    void testTriageEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/admin/triage"))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), "GET /api/v1/admin/triage should return 200");

        JsonNode json = mapper.readTree(res.body());
        assertTrue(json.has("counters"), "Response must contain 'counters'");
        assertTrue(json.has("complaints"), "Response must contain 'complaints'");
        assertTrue(json.has("total"), "Response must contain 'total'");
        assertEquals(25, json.path("size").asInt(), "Default page size must be 25");

        JsonNode counters = json.path("counters");
        long unassigned = counters.path("unassigned").asLong();
        long overdue = counters.path("overdue").asLong();
        long reopened = counters.path("reopened").asLong();
        long highPriority = counters.path("highPriority").asLong();

        assertTrue(unassigned > 0, "Seeded data must have unassigned complaints");
        assertTrue(overdue >= 0, "Overdue count must be non-negative");
        assertTrue(reopened > 0, "Seeded data must have reopened complaints");
        assertTrue(highPriority > 0, "Seeded data must have high priority open complaints");

        JsonNode complaints = json.path("complaints");
        assertTrue(complaints.isArray(), "Complaints must be an array");
        assertTrue(complaints.size() > 0, "Complaints list must not be empty");

        // First item must be unassigned or reopened (per Level 1 triage order)
        JsonNode first = complaints.get(0);
        boolean firstIsUnassignedOrReopened = first.path("staffId").isNull()
                || "REOPENED".equals(first.path("status").asText());
        assertTrue(firstIsUnassignedOrReopened, "First triage complaint must be unassigned or reopened");
    }

    @Test
    @DisplayName("GET /api/v1/admin/triage?counter=unassigned filters only unassigned complaints")
    void testFilterByUnassigned() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/admin/triage?counter=unassigned"))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());

        JsonNode json = mapper.readTree(res.body());
        JsonNode complaints = json.path("complaints");
        for (JsonNode c : complaints) {
            assertTrue(c.path("staffId").isNull(), "Filtered by unassigned must have null staffId");
        }
    }

    @Test
    @DisplayName("GET /api/v1/admin/triage?counter=reopened filters only reopened complaints")
    void testFilterByReopened() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/admin/triage?counter=reopened"))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());

        JsonNode json = mapper.readTree(res.body());
        JsonNode complaints = json.path("complaints");
        for (JsonNode c : complaints) {
            assertEquals("REOPENED", c.path("status").asText(), "Filtered by reopened must have REOPENED status");
        }
    }
}
