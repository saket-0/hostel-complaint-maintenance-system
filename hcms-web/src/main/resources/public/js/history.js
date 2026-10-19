/**
 * Complaint History & Audit Trail Module for HCMS.
 * Provides searchable, filterable historical audit records across all roles:
 * - Students: Past filed complaints, resolution dates, feedback ratings, and full audit trails.
 * - Staff: Completed maintenance tasks, resolution notes, and student reviews.
 * - Admin: Comprehensive institution-wide audit trail with CSV export for pattern analysis.
 */
import { apiRequest } from './api.js';
import { formatDate, openComplaintModal } from './student.js';
import { showToast, escapeHtml } from './toast.js';

let allHistoryItems = [];
let currentRole = null;

export async function initHistoryView(role) {
    currentRole = role;
    const listEl = document.getElementById('historyComplaintsList');
    const actionsEl = document.getElementById('historyActions');
    const searchInput = document.getElementById('historySearchInput');
    const catFilter = document.getElementById('historyCategoryFilter');
    const statusFilter = document.getElementById('historyStatusFilter');

    if (!listEl) return;
    listEl.innerHTML = '<div class="loading">Loading historical records...</div>';

    // Role-specific action buttons
    if (actionsEl) {
        if (role === 'ADMIN') {
            actionsEl.innerHTML = `
                <button class="btn btn-primary btn-sm" id="historyExportCsvBtn">📥 Export Audit CSV</button>
            `;
            const exportBtn = document.getElementById('historyExportCsvBtn');
            if (exportBtn) {
                exportBtn.onclick = async () => {
                    try {
                        const csv = await apiRequest('/admin/export.csv', { headers: { 'Accept': 'text/csv' } });
                        const blob = new Blob([csv], { type: 'text/csv' });
                        const url = window.URL.createObjectURL(blob);
                        const a = document.createElement('a');
                        a.href = url;
                        a.download = `hcms-audit-history-${new Date().toISOString().slice(0, 10)}.csv`;
                        a.click();
                        showToast('Audit history CSV downloaded', 'success');
                    } catch (err) {
                        showToast(err.message, 'error');
                    }
                };
            }
        } else {
            actionsEl.innerHTML = '';
        }
    }

    // Reset filters
    if (searchInput) searchInput.value = '';
    if (catFilter) catFilter.value = '';
    if (statusFilter) statusFilter.value = '';

    try {
        if (role === 'STUDENT') {
            const data = await apiRequest('/complaints/mine?size=100');
            allHistoryItems = (data && data.complaints) || [];
        } else if (role === 'STAFF') {
            const data = await apiRequest('/staff/tasks/history');
            allHistoryItems = (data && data.tasks) || [];
        } else if (role === 'ADMIN') {
            const data = await apiRequest('/admin/complaints?size=100');
            allHistoryItems = (data && data.complaints) || [];
        }

        renderFilteredHistory();

        // Bind filter event listeners
        if (searchInput) searchInput.oninput = () => renderFilteredHistory();
        if (catFilter) catFilter.onchange = () => renderFilteredHistory();
        if (statusFilter) statusFilter.onchange = () => renderFilteredHistory();

    } catch (err) {
        listEl.innerHTML = `<div class="toast toast-error">Failed to load history: ${escapeHtml(err.message)}</div>`;
    }
}

function renderFilteredHistory() {
    const listEl = document.getElementById('historyComplaintsList');
    const searchInput = document.getElementById('historySearchInput');
    const catFilter = document.getElementById('historyCategoryFilter');
    const statusFilter = document.getElementById('historyStatusFilter');

    const query = (searchInput ? searchInput.value : '').toLowerCase().trim();
    const selectedCat = catFilter ? catFilter.value : '';
    const selectedStatus = statusFilter ? statusFilter.value : '';

    const filtered = allHistoryItems.filter(item => {
        if (selectedCat && item.category !== selectedCat) return false;
        if (selectedStatus && item.status !== selectedStatus) return false;
        if (query) {
            const matchId = item.complaintId && item.complaintId.toLowerCase().includes(query);
            const matchDesc = item.description && item.description.toLowerCase().includes(query);
            const matchStudent = item.studentName && item.studentName.toLowerCase().includes(query);
            const matchStaff = item.staffName && item.staffName.toLowerCase().includes(query);
            if (!matchId && !matchDesc && !matchStudent && !matchStaff) return false;
        }
        return true;
    });

    // Update summary stat cards
    updateHistoryStats(allHistoryItems);

    if (filtered.length === 0) {
        listEl.innerHTML = `
            <div class="empty-state">
                <p>🔍 No complaints match your history filter criteria.</p>
                <p style="font-size: 0.85rem; color: var(--grey); margin-top: 6px;">Try adjusting your search query or status filter.</p>
            </div>
        `;
        return;
    }

    listEl.innerHTML = '';
    filtered.forEach(c => {
        const card = document.createElement('div');
        card.className = 'complaint-card history-card';

        const isResolved = c.status === 'RESOLVED';
        const isClosed = c.status === 'CLOSED';
        const feedbackRating = c.feedbackRating || (c.feedback && c.feedback.rating);
        const feedbackComment = c.feedbackComment || (c.feedback && c.feedback.comment);

        let feedbackBadge = '';
        if (feedbackRating) {
            feedbackBadge = `<span class="history-feedback-badge">⭐ ${'★'.repeat(feedbackRating)}${'☆'.repeat(5 - feedbackRating)} (${feedbackRating}/5)</span>`;
        }

        let resolutionDateHtml = '';
        if (c.resolvedDate) {
            resolutionDateHtml = `<span>✅ Resolved: ${formatDate(c.resolvedDate)}</span>`;
        } else if (c.updatedDate) {
            resolutionDateHtml = `<span>🕒 Updated: ${formatDate(c.updatedDate)}</span>`;
        }

        card.innerHTML = `
            <div class="complaint-card-header">
                <div style="display:flex; align-items:center; gap: 8px;">
                    <span class="complaint-id">${escapeHtml(c.complaintId)}</span>
                    ${feedbackBadge}
                </div>
                <div class="complaint-meta">
                    <span class="priority-badge priority-${c.priority}">${escapeHtml(c.priority)}</span>
                    <span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>
                </div>
            </div>
            <div class="complaint-card-body">
                <p><strong>${escapeHtml(c.categoryDisplay || c.category)}:</strong> ${escapeHtml(c.description)}</p>
                ${feedbackComment ? `<p style="font-size:0.85rem; color:#475569; margin-top:6px;"><em>💬 Student review: "${escapeHtml(feedbackComment)}"</em></p>` : ''}
            </div>
            <div class="complaint-card-footer">
                <span>📅 Filed: ${formatDate(c.createdDate)}</span>
                ${resolutionDateHtml}
                ${c.studentName ? `<span>👤 Student: ${escapeHtml(c.studentName)}</span>` : ''}
                ${c.staffName ? `<span>🔧 Assigned: ${escapeHtml(c.staffName)}</span>` : ''}
                <button class="btn btn-outline btn-sm btn-view-history" data-id="${c.complaintId}">📜 Full Audit Trail</button>
            </div>
        `;

        listEl.appendChild(card);
    });

    // Bind modal click events
    listEl.querySelectorAll('.btn-view-history').forEach(btn => {
        btn.onclick = () => {
            const id = btn.getAttribute('data-id');
            openComplaintModal(id);
        };
    });
}

function updateHistoryStats(items) {
    const totalEl = document.getElementById('historyTotalCount');
    const openEl = document.getElementById('historyOpenCount');
    const resolvedEl = document.getElementById('historyResolvedCount');
    const closedEl = document.getElementById('historyClosedCount');
    const ratingEl = document.getElementById('historyAvgRating');

    if (!items || items.length === 0) {
        if (totalEl) totalEl.textContent = '0';
        if (openEl) openEl.textContent = '0';
        if (resolvedEl) resolvedEl.textContent = '0';
        if (closedEl) closedEl.textContent = '0';
        if (ratingEl) ratingEl.textContent = '—';
        return;
    }

    const total = items.length;
    const open = items.filter(i => ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED'].includes(i.status)).length;
    const resolved = items.filter(i => i.status === 'RESOLVED').length;
    const closed = items.filter(i => i.status === 'CLOSED').length;

    let ratedCount = 0;
    let sumRating = 0;
    items.forEach(i => {
        const r = i.feedbackRating || (i.feedback && i.feedback.rating);
        if (r) {
            sumRating += r;
            ratedCount++;
        }
    });

    if (totalEl) totalEl.textContent = total;
    if (openEl) openEl.textContent = open;
    if (resolvedEl) resolvedEl.textContent = resolved;
    if (closedEl) closedEl.textContent = closed;
    if (ratingEl) {
        ratingEl.textContent = ratedCount > 0 ? `${(sumRating / ratedCount).toFixed(1)} / 5` : '—';
    }
}
