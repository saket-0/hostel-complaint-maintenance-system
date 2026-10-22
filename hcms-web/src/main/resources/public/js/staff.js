/**
 * Maintenance Staff Dashboard Module.
 * Displays assigned tasks, accept task action, update status to RESOLVED with note,
 * toggle availability, and view completed tasks history.
 */
import { apiRequest } from './api.js';
import { showToast, escapeHtml } from './toast.js';
import { openComplaintModal } from './student.js';
import { formatExactDate, formatRelativeDate, formatDueStatus } from './dates.js';
import { getIcon } from './icons.js';
import { getPriorityInfo } from './app.js';

let currentModeIsCompleted = false;

export function initStaffView(isCompleted = false) {
    currentModeIsCompleted = isCompleted;

    const titleEl = document.getElementById('staffDashboardTitle');
    const availContainer = document.getElementById('staffAvailabilityContainer');
    const toggle = document.getElementById('availabilityToggle');

    if (titleEl) {
        titleEl.textContent = isCompleted ? 'Completed Tasks' : 'My Tasks';
    }

    if (availContainer) {
        availContainer.style.display = isCompleted ? 'none' : 'flex';
    }

    if (toggle && !isCompleted) {
        toggle.onchange = async () => {
            const available = toggle.checked;
            try {
                await apiRequest('/staff/availability', {
                    method: 'PUT',
                    body: { available }
                });
                showToast(`Status updated: ${available ? 'Available' : 'Busy'}`, 'info');
            } catch (err) {
                toggle.checked = !available;
                showToast(err.message, 'error');
            }
        };
    }

    loadStaffTasks(isCompleted);
}

export async function loadStaffTasks(isCompleted = false) {
    const listEl = document.getElementById('staffTasks');
    if (!listEl) return;

    listEl.innerHTML = `
        <div class="skeleton-card" style="height:120px; border-radius:4px; margin-bottom:12px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
        <div class="skeleton-card" style="height:120px; border-radius:4px; margin-bottom:12px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
    `;

    try {
        const endpoint = isCompleted ? '/staff/tasks/history' : '/staff/tasks';
        const data = await apiRequest(endpoint);
        const tasks = (data && data.tasks) || [];

        if (tasks.length === 0) {
            listEl.innerHTML = `
                <div class="empty-state" style="padding: 40px 20px; text-align: center;">
                    <div class="empty-icon" style="margin-bottom: 12px; color: var(--grey);">${getIcon('inbox', '', 40)}</div>
                    <h3 style="margin-bottom: 6px;">${isCompleted ? 'No completed tasks yet' : 'All caught up!'}</h3>
                    <p style="color: var(--grey);">${isCompleted ? 'Tasks you mark as resolved will appear here.' : 'No pending maintenance tasks assigned to you right now.'}</p>
                </div>
            `;
            return;
        }

        listEl.innerHTML = '';
        tasks.forEach(task => {
            const card = document.createElement('div');
            card.className = 'complaint-card';
            card.style.cursor = 'pointer';

            const isOverdue = task.overdue ? '<span class="badge badge-overdue" style="margin-left:4px;">OVERDUE</span>' : '';
            const pInfo = getPriorityInfo(task.priority);
            const pLabel = pInfo.label;
            const pClass = pInfo.class;

            const allowed = task.allowedActions || [];
            let actionHtml = '';
            if (allowed.includes('ACCEPT')) {
                actionHtml = `<button class="btn btn-primary btn-sm btn-accept" data-id="${task.complaintId}">Accept Task</button>`;
            } else if (allowed.includes('RESOLVE')) {
                actionHtml = `<button class="btn btn-teal btn-sm btn-resolve" data-id="${task.complaintId}">Mark as Resolved</button>`;
            }

            const feedbackRating = task.feedbackRating;
            const feedbackComment = task.feedbackComment;
            let feedbackHtml = '';
            if (feedbackRating) {
                feedbackHtml = `<div class="task-review" style="margin-top: 8px; padding: 6px 10px; background: #fffbeb; border: 1px solid #fde68a; border-radius: 4px; font-size: 0.85rem;">
                    <span style="color: #b45309; font-weight: 700;">★ ${feedbackRating}/5:</span>
                    ${feedbackComment ? `<em>"${escapeHtml(feedbackComment)}"</em>` : ''}
                </div>`;
            }

            const dateDisplay = isCompleted 
                ? `<span title="${formatExactDate(task.resolvedDate || task.updatedDate)}">Resolved ${formatRelativeDate(task.resolvedDate || task.updatedDate)}</span>`
                : `<span title="${formatExactDate(task.dueBy)}">${formatDueStatus(task.dueBy, task.status)}</span>`;

            const blockVal = task.hostelBlock || task.studentHostelBlock || '';
            const roomVal = task.roomNumber || task.studentRoomNumber || '';
            const locationTag = (blockVal || roomVal)
                ? `<span class="meta-tag" title="Hostel Location">🏢 ${escapeHtml(blockVal)}${blockVal && roomVal ? ' - ' : ''}${escapeHtml(roomVal)}</span>`
                : '';

            card.innerHTML = `
                <div class="complaint-card-header">
                    <span class="complaint-id font-mono">${escapeHtml(task.complaintId)}</span>
                    <div class="complaint-meta">
                        <span class="priority-badge ${pClass}">${escapeHtml(pLabel)}</span>
                        <span class="status-badge status-${task.status}">${escapeHtml(task.statusDisplay || task.status)}</span>
                        ${isOverdue}
                    </div>
                </div>
                <div class="complaint-card-body">
                    <p><strong>${escapeHtml(task.categoryDisplay || task.category)}:</strong> ${escapeHtml(task.description)}</p>
                    ${feedbackHtml}
                </div>
                <div class="complaint-card-footer" style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px;">
                    <div style="display:inline-flex; align-items:center; gap:8px; flex-wrap:wrap;">
                        <span style="display:inline-flex; align-items:center; gap:4px; font-size:0.85rem; font-weight:600; color:var(--navy);">
                            ${getIcon('user', '', 14)} ${escapeHtml(task.studentName || 'Student')}
                        </span>
                        ${locationTag}
                    </div>
                    <span style="font-size:0.85rem;">${dateDisplay}</span>
                    <div style="display:flex; gap: 8px; align-items:center;">
                        <button class="btn btn-outline btn-sm btn-view-details" data-id="${task.complaintId}">Details</button>
                        ${actionHtml}
                    </div>
                </div>
            `;
            listEl.appendChild(card);
        });

        // Bind events: entire card is clickable except action buttons
        listEl.querySelectorAll('.complaint-card').forEach(card => {
            card.addEventListener('click', (e) => {
                if (e.target.closest('.btn-accept') || e.target.closest('.btn-resolve')) return;
                const detailsBtn = card.querySelector('.btn-view-details');
                if (detailsBtn) {
                    openComplaintModal(detailsBtn.getAttribute('data-id'));
                }
            });
        });

        listEl.querySelectorAll('.btn-view-details').forEach(btn => {
            btn.onclick = (e) => {
                e.stopPropagation();
                openComplaintModal(btn.getAttribute('data-id'));
            };
        });

        listEl.querySelectorAll('.btn-accept').forEach(btn => {
            btn.onclick = async (e) => {
                e.stopPropagation();
                const id = btn.getAttribute('data-id');
                try {
                    await apiRequest(`/staff/tasks/${id}/accept`, { method: 'POST' });
                    showToast(`Task ${id} accepted. Status: IN PROGRESS`, 'success');
                    loadStaffTasks(currentModeIsCompleted);
                } catch (err) {
                    showToast(err.message, 'error');
                }
            };
        });

        listEl.querySelectorAll('.btn-resolve').forEach(btn => {
            btn.onclick = async (e) => {
                e.stopPropagation();
                const id = btn.getAttribute('data-id');
                const note = prompt('Please enter resolution note / work done:');
                if (note === null) return;
                try {
                    await apiRequest(`/staff/tasks/${id}/status`, {
                        method: 'PUT',
                        body: {
                            status: 'RESOLVED',
                            note: note || 'Resolved by staff member'
                        }
                    });
                    showToast(`Task ${id} marked as RESOLVED!`, 'success');
                    loadStaffTasks(currentModeIsCompleted);
                } catch (err) {
                    showToast(err.message, 'error');
                }
            };
        });

    } catch (err) {
        listEl.innerHTML = `
            <div class="inline-error-state" style="padding: 24px; text-align: center; border: 1px solid var(--border-color); background: #fff; margin: 16px 0;">
                <div style="color: var(--crimson); margin-bottom: 8px;">${getIcon('alert-triangle', '', 28)}</div>
                <p style="margin-bottom: 12px; font-weight: 600;">Failed to load tasks: ${escapeHtml(err.message)}</p>
                <button class="btn btn-outline btn-sm staff-retry-btn" type="button">${getIcon('refresh-cw', '', 14)} Retry</button>
            </div>
        `;
        listEl.querySelector('.staff-retry-btn')?.addEventListener('click', () => loadStaffTasks(isCompleted));
    }
}
