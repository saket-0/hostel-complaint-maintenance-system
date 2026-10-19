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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("API Test: List status == Detail status consistency across all seeded complaints")
class ComplaintStatusConsistencyIT {

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
        assertFalse(adminToken.isBlank(), "Admin token must not be blank");
    }

    @AfterAll
    static void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName("Every seeded complaint has identical status in list and detail endpoints")
    void testListStatusMatchesDetailStatusForEverySeededComplaint() throws Exception {
        HttpRequest listReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/admin/complaints?size=100"))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build();

        HttpResponse<String> listRes = client.send(listReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listRes.statusCode(), "Admin complaints list must return 200 OK");

        JsonNode rootNode = mapper.readTree(listRes.body());
        JsonNode listData = rootNode.path("complaints");
        assertTrue(listData.isArray(), "List response must have 'complaints' JSON array");
        assertTrue(listData.size() >= 40, "Expected at least 40 seeded complaints, got: " + listData.size());

        for (JsonNode item : listData) {
            String complaintId = item.path("complaintId").asText();
            String listStatus = item.path("status").asText();
            assertNotNull(complaintId);
            assertNotNull(listStatus);

            // Fetch detail endpoint
            HttpRequest detailReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/v1/complaints/" + complaintId))
                    .header("Authorization", "Bearer " + adminToken)
                    .GET()
                    .build();

            HttpResponse<String> detailRes = client.send(detailReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, detailRes.statusCode(), "Detail request for " + complaintId + " must return 200 OK");

            JsonNode detailData = mapper.readTree(detailRes.body());
            String detailStatus = detailData.path("status").asText();

            assertEquals(listStatus, detailStatus,
                    "Complaint " + complaintId + " status in list (" + listStatus + ") must match detail (" + detailStatus + ")");

            // Verify assignedStaffName consistency
            if ("ASSIGNED".equals(listStatus) || "IN_PROGRESS".equals(listStatus)) {
                String listStaff = item.path("assignedStaffName").asText();
                String detailStaff = detailData.path("assignedStaffName").asText();
                assertFalse(listStaff.isBlank() || "null".equals(listStaff),
                        "Complaint " + complaintId + " should have assigned staff name in list view");
                assertEquals(listStaff, detailStaff,
                        "Complaint " + complaintId + " assigned staff name in list must match detail");
            }
        }
    }
}
