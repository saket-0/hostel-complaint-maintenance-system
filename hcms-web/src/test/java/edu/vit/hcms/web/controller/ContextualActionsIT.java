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

@DisplayName("API Test: Contextual Actions and State Machine Error Enforcement")
class ContextualActionsIT {

    private static Javalin app;
    private static int port;
    private static HttpClient client;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static String adminToken;
    private static String studentToken;
    private static String staffToken;

    @BeforeAll
    static void setUp() throws Exception {
        app = HcmsApplication.createServer(0);
        port = app.port();
        client = HttpClient.newHttpClient();

        adminToken = login("admin@vit.ac.in", "password123");
        studentToken = login("priya.sharma@vitstudent.ac.in", "password123");
        staffToken = login("arun.kumar@vit.ac.in", "password123");
    }

    private static String login(String email, String password) throws Exception {
        String payload = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        JsonNode root = mapper.readTree(res.body());
        return root.path("token").asText();
    }

    @AfterAll
    static void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName("Admin complaints list includes allowedActions for each complaint")
    void testAdminListIncludesAllowedActions() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/admin/complaints?size=10"))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        JsonNode root = mapper.readTree(res.body());
        JsonNode complaints = root.path("complaints");
        assertTrue(complaints.isArray());

        for (JsonNode c : complaints) {
            JsonNode allowed = c.path("allowedActions");
            assertTrue(allowed.isArray(), "allowedActions must be an array");
            String status = c.path("status").asText();
            if ("NEW".equals(status)) {
                assertTrue(allowed.toString().contains("ASSIGN"), "NEW complaint must allow ASSIGN for admin");
            } else if ("ASSIGNED".equals(status) || "IN_PROGRESS".equals(status)) {
                assertTrue(allowed.toString().contains("REASSIGN"), "ASSIGNED/IN_PROGRESS complaint must allow REASSIGN for admin");
            }
        }
    }

    @Test
    @DisplayName("Student submitting feedback on non-resolved complaint fails with 422")
    void testFeedbackOnNonResolvedFails() throws Exception {
        // Register a new complaint first
        String createPayload = "{\"category\":\"ELECTRICAL\",\"description\":\"Test short circuit in bathroom socket\"}";
        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/complaints"))
                .header("Authorization", "Bearer " + studentToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createPayload))
                .build();

        HttpResponse<String> createRes = client.send(createReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createRes.statusCode());
        String complaintId = mapper.readTree(createRes.body()).path("complaintId").asText();

        // Attempt to submit feedback on NEW complaint
        String fbPayload = "{\"rating\":5,\"comment\":\"Great service\"}";
        HttpRequest fbReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/complaints/" + complaintId + "/feedback"))
                .header("Authorization", "Bearer " + studentToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(fbPayload))
                .build();

        HttpResponse<String> fbRes = client.send(fbReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, fbRes.statusCode(), "Submitting feedback on a non-resolved complaint must return 422");
    }

    @Test
    @DisplayName("Staff updating status of unassigned or new complaint to resolved returns 409")
    void testInvalidStatusTransitionReturns409() throws Exception {
        // Register a new complaint
        String createPayload = "{\"category\":\"PLUMBING\",\"description\":\"Water leaking from washbasin pipe\"}";
        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/complaints"))
                .header("Authorization", "Bearer " + studentToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createPayload))
                .build();

        HttpResponse<String> createRes = client.send(createReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createRes.statusCode());
        String complaintId = mapper.readTree(createRes.body()).path("complaintId").asText();

        // Staff tries to resolve NEW complaint directly without it being assigned or in progress
        String updatePayload = "{\"status\":\"RESOLVED\",\"note\":\"Done\"}";
        HttpRequest updateReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/staff/tasks/" + complaintId + "/status"))
                .header("Authorization", "Bearer " + staffToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updatePayload))
                .build();

        HttpResponse<String> updateRes = client.send(updateReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(409, updateRes.statusCode(), "Illegal status transition must return 409 Conflict");
    }
}
