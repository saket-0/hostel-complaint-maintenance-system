package edu.vit.hcms.web.controller;

import edu.vit.hcms.core.model.Notification;
import edu.vit.hcms.service.notification.NotificationService;
import io.javalin.http.Context;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for notification operations: list and mark-as-read.
 */
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** GET /api/v1/notifications */
    public void getNotifications(Context ctx) {
        Long userId = ctx.attribute("userId");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);

        List<Notification> notifications = notificationService.getNotifications(userId, limit);
        int unreadCount = notificationService.getUnreadCount(userId);

        List<Map<String, Object>> result = notifications.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("message", n.getMessage());
            m.put("read", n.isRead());
            m.put("createdAt", n.getCreatedAt().toString());
            return m;
        }).collect(Collectors.toList());

        ctx.json(Map.of("notifications", result, "unreadCount", unreadCount));
    }

    /** POST /api/v1/notifications/{id}/read */
    public void markAsRead(Context ctx) {
        Long notificationId = Long.parseLong(ctx.pathParam("id"));
        Long userId = ctx.attribute("userId");
        notificationService.markAsRead(notificationId, userId);
        ctx.json(Map.of("message", "Marked as read"));
    }
}
