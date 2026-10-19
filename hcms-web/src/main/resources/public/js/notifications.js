/**
 * Notification Center Module.
 * Polls unread notifications every 15 seconds, updates bell badge,
 * and handles mark-as-read.
 */
import { apiRequest } from './api.js';
import { escapeHtml } from './toast.js';
import { formatDate } from './student.js';

let pollInterval = null;

export function initNotifications() {
    const bellBtn = document.getElementById('notifBell');
    const panel = document.getElementById('notifPanel');

    if (bellBtn && panel) {
        bellBtn.onclick = (e) => {
            e.stopPropagation();
            panel.style.display = panel.style.display === 'none' ? 'block' : 'none';
            if (panel.style.display === 'block') {
                loadNotifications();
            }
        };

        document.addEventListener('click', (e) => {
            if (!panel.contains(e.target) && e.target !== bellBtn) {
                panel.style.display = 'none';
            }
        });
    }

    // Initial load and start 15s polling
    loadNotifications();
    if (pollInterval) clearInterval(pollInterval);
    pollInterval = setInterval(loadNotifications, 15000);
}

export function stopNotificationPolling() {
    if (pollInterval) {
        clearInterval(pollInterval);
        pollInterval = null;
    }
}

export async function loadNotifications() {
    const badge = document.getElementById('notifBadge');
    const list = document.getElementById('notifList');

    try {
        const data = await apiRequest('/notifications');
        const notifs = data.notifications || [];
        const unreadCount = data.unreadCount || 0;

        if (badge) {
            if (unreadCount > 0) {
                badge.textContent = unreadCount;
                badge.style.display = 'block';
            } else {
                badge.style.display = 'none';
            }
        }

        if (list) {
            if (notifs.length === 0) {
                list.innerHTML = '<p style="color:#666; font-size:0.85rem; padding: 10px 0;">No notifications.</p>';
                return;
            }

            let html = '';
            notifs.forEach(n => {
                const unreadClass = !n.readFlag ? 'unread' : '';
                html += `
                    <div class="notif-item ${unreadClass}" data-id="${n.id}">
                        <div>${escapeHtml(n.message)}</div>
                        <div class="notif-time">${formatDate(n.createdAt)}</div>
                    </div>
                `;
            });
            list.innerHTML = html;

            list.querySelectorAll('.notif-item.unread').forEach(el => {
                el.onclick = async () => {
                    const id = el.getAttribute('data-id');
                    try {
                        await apiRequest(`/notifications/${id}/read`, { method: 'POST' });
                        el.classList.remove('unread');
                        loadNotifications();
                    } catch (e) {
                        // ignore error
                    }
                };
            });
        }
    } catch (e) {
        // user might not be logged in yet
    }
}
