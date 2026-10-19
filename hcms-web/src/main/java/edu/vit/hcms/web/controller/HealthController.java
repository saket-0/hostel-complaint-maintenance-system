package edu.vit.hcms.web.controller;

import edu.vit.hcms.persistence.db.DatabaseManager;
import edu.vit.hcms.service.notification.NotificationService;
import edu.vit.hcms.web.HcmsApplication;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller for health check and metrics endpoints.
 * Provides system status for Docker healthchecks and the "Built with Java" footer.
 */
public class HealthController {

    private final DatabaseManager dbManager;
    private final NotificationService notificationService;

    public HealthController(DatabaseManager dbManager, NotificationService notificationService) {
        this.dbManager = dbManager;
        this.notificationService = notificationService;
    }

    /** GET /api/v1/health - Database connectivity check for Docker healthcheck. */
    public void health(Context ctx) {
        boolean dbHealthy = dbManager.isHealthy();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", dbHealthy ? "UP" : "DOWN");
        response.put("database", dbHealthy ? "connected" : "disconnected");
        ctx.status(dbHealthy ? 200 : 503).json(response);
    }

    /** GET /api/v1/metrics-lite - JVM stats for the "Built with Java" footer. */
    public void metrics(Context ctx) {
        long uptimeMs = System.currentTimeMillis() - HcmsApplication.getStartTime();
        long uptimeSeconds = uptimeMs / 1000;
        long hours = uptimeSeconds / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        long seconds = uptimeSeconds % 60;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("javaVersion", System.getProperty("java.version"));
        response.put("javaVendor", System.getProperty("java.vendor"));
        response.put("uptime", String.format("%02d:%02d:%02d", hours, minutes, seconds));
        response.put("uptimeMs", uptimeMs);
        response.put("activeThreads", Thread.activeCount());
        response.put("notificationQueueDepth", notificationService.getQueueDepth());
        response.put("poolStats", dbManager.getPoolStats());
        response.put("maxMemoryMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        response.put("usedMemoryMb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024));
        response.put("availableProcessors", Runtime.getRuntime().availableProcessors());

        ctx.json(response);
    }
}
