package edu.vit.hcms.web;

import edu.vit.hcms.persistence.dao.ComplaintDao;
import edu.vit.hcms.persistence.dao.SupportDao;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.persistence.db.DatabaseManager;
import edu.vit.hcms.persistence.db.TransactionManager;
import edu.vit.hcms.service.analytics.AnalyticsService;
import edu.vit.hcms.service.auth.AuthService;
import edu.vit.hcms.service.complaint.ComplaintManager;
import edu.vit.hcms.service.engine.SlaEscalationEngine;
import edu.vit.hcms.service.notification.AuditListener;
import edu.vit.hcms.service.notification.NotificationService;
import edu.vit.hcms.service.strategy.LeastWorkloadStrategy;
import edu.vit.hcms.service.strategy.SpecializationMatchStrategy;
import edu.vit.hcms.web.controller.*;
import edu.vit.hcms.web.filter.AuthFilter;
import edu.vit.hcms.web.filter.CorsFilter;
import edu.vit.hcms.web.filter.RateLimitFilter;
import edu.vit.hcms.web.handler.GlobalExceptionHandler;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vit.hcms.core.config.AppConfig;

/**
 * Main entry point for the HCMS application.
 * Wires up all layers (persistence, service, web) and starts Javalin.
 * Registers graceful shutdown hooks for all resources.
 */
public class HcmsApplication {

    private static final Logger log = LoggerFactory.getLogger(HcmsApplication.class);
    private static final long START_TIME = System.currentTimeMillis();

    public static long getStartTime() {
        return START_TIME;
    }

    public static void main(String[] args) {
        log.info("Starting HCMS - Hostel Complaint & Maintenance System");
        AppConfig config = AppConfig.getInstance();

        // ---- Persistence Layer ----
        DatabaseManager dbManager = new DatabaseManager();
        TransactionManager txManager = new TransactionManager(dbManager.getDataSource());

        // ---- DAOs ----
        UserDao userDao = new UserDao(txManager);
        ComplaintDao complaintDao = new ComplaintDao(txManager);
        SupportDao supportDao = new SupportDao(txManager);

        // ---- Service Layer ----
        AuthService authService = new AuthService(userDao, txManager);
        NotificationService notificationService = new NotificationService(supportDao, 2);
        AuditListener auditListener = new AuditListener(supportDao);

        ComplaintManager complaintManager = new ComplaintManager(complaintDao, supportDao, userDao, txManager);
        complaintManager.addStatusListener(notificationService);
        complaintManager.addStatusListener(auditListener);
        complaintManager.registerStrategy("specialization", new SpecializationMatchStrategy());
        complaintManager.registerStrategy("leastWorkload", new LeastWorkloadStrategy(userDao));

        AnalyticsService analyticsService = new AnalyticsService(complaintDao, userDao);

        SlaEscalationEngine slaEngine = new SlaEscalationEngine(complaintDao, notificationService);
        slaEngine.start();

        // ---- Controllers ----
        AuthController authController = new AuthController(authService, userDao);
        ComplaintController complaintController = new ComplaintController(complaintManager, supportDao, userDao);
        AdminController adminController = new AdminController(complaintManager, complaintDao, userDao, analyticsService, supportDao);
        StaffController staffController = new StaffController(complaintManager, userDao);
        NotificationController notificationController = new NotificationController(notificationService);
        HealthController healthController = new HealthController(dbManager, notificationService);

        // ---- Filters ----
        AuthFilter authFilter = new AuthFilter(authService, userDao);
        RateLimitFilter rateLimitFilter = new RateLimitFilter();

        // ---- Javalin App ----
        int port = config.getInt("PORT");
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.staticFiles.add("/public", Location.CLASSPATH);
            javalinConfig.http.maxRequestSize = config.getLong("MAX_UPLOAD_SIZE_BYTES") + 1024;
            javalinConfig.showJavalinBanner = false;
        });

        // Global exception handling
        GlobalExceptionHandler.register(app);

        // CORS
        CorsFilter.register(app);

        // Security headers
        app.before(ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("X-Frame-Options", "DENY");
            ctx.header("X-XSS-Protection", "1; mode=block");
            ctx.header("Referrer-Policy", "strict-origin-when-cross-origin");
        });

        // Request logging with correlation IDs
        app.before(ctx -> {
            String correlationId = java.util.UUID.randomUUID().toString().substring(0, 8);
            ctx.attribute("correlationId", correlationId);
            log.debug("[{}] {} {}", correlationId, ctx.method(), ctx.path());
        });

        // ---- Auth Routes ----
        app.post("/api/v1/auth/register", ctx -> {
            rateLimitFilter.checkLimit(ctx, "register", 10);
            authController.register(ctx);
        });
        app.post("/api/v1/auth/login", ctx -> {
            rateLimitFilter.checkLimit(ctx, "login", config.getInt("RATE_LIMIT_LOGIN"));
            authController.login(ctx);
        });
        app.post("/api/v1/auth/logout", authController::logout);
        app.get("/api/v1/auth/me", ctx -> {
            authFilter.requireAuth(ctx);
            authController.me(ctx);
        });
        app.post("/api/v1/auth/change-password", ctx -> {
            authFilter.requireAuth(ctx);
            authController.changePassword(ctx);
        });

        // ---- Student Routes ----
        app.post("/api/v1/complaints", ctx -> {
            authFilter.requireRole(ctx, "STUDENT");
            rateLimitFilter.checkLimit(ctx, "complaint", config.getInt("RATE_LIMIT_COMPLAINT"));
            complaintController.create(ctx);
        });
        app.get("/api/v1/complaints/mine", ctx -> {
            authFilter.requireRole(ctx, "STUDENT");
            complaintController.getMyComplaints(ctx);
        });
        app.get("/api/v1/complaints/{id}", ctx -> {
            authFilter.requireAuth(ctx);
            complaintController.getById(ctx);
        });
        app.post("/api/v1/complaints/{id}/reopen", ctx -> {
            authFilter.requireRole(ctx, "STUDENT");
            complaintController.reopen(ctx);
        });
        app.post("/api/v1/complaints/{id}/close", ctx -> {
            authFilter.requireRole(ctx, "STUDENT");
            complaintController.close(ctx);
        });
        app.post("/api/v1/complaints/{id}/feedback", ctx -> {
            authFilter.requireRole(ctx, "STUDENT");
            complaintController.addFeedback(ctx);
        });
        app.post("/api/v1/complaints/{id}/comments", ctx -> {
            authFilter.requireAuth(ctx);
            complaintController.addComment(ctx);
        });
        app.get("/api/v1/complaints/{id}/comments", ctx -> {
            authFilter.requireAuth(ctx);
            complaintController.getComments(ctx);
        });
        app.get("/api/v1/complaints/{id}/history", ctx -> {
            authFilter.requireAuth(ctx);
            complaintController.getHistory(ctx);
        });

        // ---- Admin Routes ----
        app.get("/api/v1/admin/complaints", ctx -> {
            authFilter.requireRole(ctx, "ADMIN");
            adminController.listComplaints(ctx);
        });
        app.put("/api/v1/admin/complaints/{id}/priority", ctx -> {
            authFilter.requireRole(ctx, "ADMIN");
            adminController.updatePriority(ctx);
        });
        app.post("/api/v1/admin/complaints/{id}/assign", ctx -> {
            authFilter.requireRole(ctx, "ADMIN");
            adminController.assign(ctx);
        });
        app.post("/api/v1/admin/complaints/{id}/auto-assign", ctx -> {
            authFilter.requireRole(ctx, "ADMIN");
            adminController.autoAssign(ctx);
        });
        app.get("/api/v1/admin/staff", ctx -> {
            authFilter.requireRole(ctx, "ADMIN");
            adminController.listStaff(ctx);
        });
        app.post("/api/v1/admin/staff", ctx -> {
            authFilter.requireRole(ctx, "ADMIN");
            adminController.createStaff(ctx);
        });
        app.get("/api/v1/admin/analytics", ctx -> {
            authFilter.requireRole(ctx, "ADMIN");
            adminController.analytics(ctx);
        });
        app.get("/api/v1/admin/export.csv", ctx -> {
            authFilter.requireRole(ctx, "ADMIN");
            adminController.exportCsv(ctx);
        });
        app.get("/api/v1/admin/export", ctx -> {
            authFilter.requireRole(ctx, "ADMIN");
            adminController.exportCsv(ctx);
        });

        // ---- Staff Routes ----
        app.get("/api/v1/staff/tasks", ctx -> {
            authFilter.requireRole(ctx, "STAFF");
            staffController.getTasks(ctx);
        });
        app.post("/api/v1/staff/tasks/{id}/accept", ctx -> {
            authFilter.requireRole(ctx, "STAFF");
            staffController.acceptTask(ctx);
        });
        app.put("/api/v1/staff/tasks/{id}/status", ctx -> {
            authFilter.requireRole(ctx, "STAFF");
            staffController.updateStatus(ctx);
        });
        app.put("/api/v1/staff/availability", ctx -> {
            authFilter.requireRole(ctx, "STAFF");
            staffController.updateAvailability(ctx);
        });
        app.get("/api/v1/staff/tasks/history", ctx -> {
            authFilter.requireRole(ctx, "STAFF");
            staffController.getTaskHistory(ctx);
        });

        // ---- Common Routes ----
        app.get("/api/v1/notifications", ctx -> {
            authFilter.requireAuth(ctx);
            notificationController.getNotifications(ctx);
        });
        app.post("/api/v1/notifications/{id}/read", ctx -> {
            authFilter.requireAuth(ctx);
            notificationController.markAsRead(ctx);
        });
        app.get("/health", healthController::health);
        app.get("/metrics-lite", healthController::metrics);
        app.get("/api/v1/health", healthController::health);
        app.get("/api/v1/metrics-lite", healthController::metrics);

        // SPA fallback: serve index.html for any non-API, non-static path
        app.get("/", ctx -> ctx.redirect("/index.html"));

        // ---- Graceful Shutdown ----
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down HCMS...");
            app.stop();
            slaEngine.close();
            notificationService.close();
            auditListener.close();
            dbManager.close();
            log.info("HCMS shutdown complete");
        }, "shutdown-hook"));

        app.start(port);
        log.info("HCMS started on port {} (profile: {})", port, config.get("PROFILE"));
    }
}
