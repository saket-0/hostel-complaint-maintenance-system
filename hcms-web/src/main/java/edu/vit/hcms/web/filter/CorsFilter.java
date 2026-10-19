package edu.vit.hcms.web.filter;

import edu.vit.hcms.core.config.AppConfig;
import io.javalin.Javalin;

/**
 * CORS configuration filter. Configurable origin via CORS_ORIGIN env var.
 */
public class CorsFilter {

    private CorsFilter() {}

    /** Registers CORS headers on every response. */
    public static void register(Javalin app) {
        String origin = AppConfig.getInstance().get("CORS_ORIGIN");
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", origin);
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            ctx.header("Access-Control-Allow-Credentials", "true");
            ctx.header("Access-Control-Max-Age", "3600");
        });
        // Handle preflight
        app.options("/*", ctx -> ctx.status(204));
    }
}
