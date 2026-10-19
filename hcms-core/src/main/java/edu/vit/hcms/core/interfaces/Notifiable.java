package edu.vit.hcms.core.interfaces;

/**
 * Interface for entities that can trigger notifications on state changes.
 * Implemented by services that manage notifiable domain objects.
 * Part of the Observer pattern: notifiable subjects notify their observers.
 */
public interface Notifiable {

    /**
     * Sends a notification about a state change to the specified user.
     *
     * @param userId  the recipient user ID
     * @param message the notification message
     */
    void notifyUser(Long userId, String message);
}
