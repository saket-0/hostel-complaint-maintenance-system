/**
 * Date Formatting and SLA Timeline Utilities.
 * Provides relative dates with exact timestamp tooltips, and explicit overdue/due-soon text.
 */
import { escapeHtml } from './toast.js';

/**
 * Formats a date string into a readable Indian Standard Time string.
 * @param {string|Date} dateStr 
 * @returns {string} e.g. "Oct 19, 2026, 08:30 PM"
 */
export function formatExactDate(dateStr) {
    if (!dateStr) return '—';
    try {
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return dateStr;
        return d.toLocaleDateString('en-IN', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (e) {
        return dateStr;
    }
}

/**
 * Formats a date string into a relative time description with an exact timestamp tooltip.
 * @param {string|Date} dateStr 
 * @returns {string} HTML snippet with tooltip
 */
export function formatRelativeDate(dateStr) {
    if (!dateStr) return '—';
    try {
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return dateStr;
        const now = Date.now();
        const diffMs = now - d.getTime();
        const exact = formatExactDate(dateStr);

        let relative = '';
        if (diffMs < 0) {
            // Future date
            const futureMs = -diffMs;
            if (futureMs < 60000) relative = 'in moments';
            else if (futureMs < 3600000) relative = `in ${Math.floor(futureMs / 60000)}m`;
            else if (futureMs < 86400000) relative = `in ${Math.floor(futureMs / 3600000)}h`;
            else relative = `in ${Math.floor(futureMs / 86400000)}d`;
        } else {
            // Past date
            if (diffMs < 60000) relative = 'just now';
            else if (diffMs < 3600000) relative = `${Math.floor(diffMs / 60000)}m ago`;
            else if (diffMs < 86400000) relative = `${Math.floor(diffMs / 3600000)}h ago`;
            else if (diffMs < 604800000) relative = `${Math.floor(diffMs / 86400000)}d ago`;
            else {
                return `<span class="date-tooltip" title="${escapeHtml(exact)}">${d.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' })}</span>`;
            }
        }

        return `<span class="date-tooltip" title="${escapeHtml(exact)}" style="cursor:help; border-bottom: 1px dotted currentColor;">${escapeHtml(relative)}</span>`;
    } catch (e) {
        return dateStr;
    }
}

/**
 * Formats a Due Date with explicit text status (Overdue by X, Due soon in Y, or normal).
 * @param {string|Date} dueByStr 
 * @param {string} [status] Complaint status
 * @returns {string} HTML snippet with explicit status text
 */
export function formatDueStatus(dueByStr, status) {
    if (!dueByStr) return '—';
    try {
        const due = new Date(dueByStr);
        if (isNaN(due.getTime())) return dueByStr;
        const exact = formatExactDate(dueByStr);
        const isFinished = status === 'RESOLVED' || status === 'CLOSED';

        if (isFinished) {
            return `<span title="Target was: ${escapeHtml(exact)}">${formatExactDate(dueByStr)}</span>`;
        }

        const now = Date.now();
        const diffMs = due.getTime() - now;

        if (diffMs < 0) {
            // Overdue
            const overdueMs = -diffMs;
            const hours = Math.floor(overdueMs / 3600000);
            const days = Math.floor(hours / 24);
            const text = days > 0 ? `${days}d overdue` : `${Math.max(1, hours)}h overdue`;
            return `<span class="badge-overdue text-red font-bold" title="Due: ${escapeHtml(exact)}" style="color:#D90429; font-weight:800;">⚠️ ${text}</span>`;
        } else if (diffMs < 4 * 3600000) {
            // Due soon (< 4 hours)
            const hours = Math.max(1, Math.floor(diffMs / 3600000));
            return `<span class="badge-due-soon text-amber font-bold" title="Due: ${escapeHtml(exact)}" style="color:#B45309; font-weight:800;">⏱ Due in ${hours}h</span>`;
        } else {
            return formatRelativeDate(dueByStr);
        }
    } catch (e) {
        return dueByStr;
    }
}
