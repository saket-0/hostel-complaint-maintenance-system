/**
 * Maintenance Staff Dashboard Module.
 * Displays assigned tasks, accept task action, update status to RESOLVED with note,
 * and toggle availability.
 */
import { apiRequest } from './api.js';
import { showToast, escapeHtml } from './toast.js';
import { formatDate, openComplaintModal } from './student.js';

export function initStaffView() {
    const toggle = document.getElementById('availabilityToggle');
    if (toggle) {
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

    loadStaffTasks();
}

export async function loadStaffTasks() {
    const listEl = document.getElementById('staffTasks');
    if (!listEl) return;

    listEl.innerHTML = '<div class="loading">Loading assigned tasks...</div>';

    try {
        const data = await apiRequest('/staff/tasks');
        const tasks = (data && data.tasks) || [];

        if (tasks.length === 0) {
            listEl.innerHTML = `
                <div class="empty-state">
                    <p>🎉 All caught up!</p>
                    <p>No pending maintenance tasks assigned to you right now.</p>
                </div>
            `;
            return;
        }

        listEl.innerHTML = '';
        tasks.forEach(task => {
            const card = document.createElement('div');
            card.className = 'complaint-card';
            
            const isOverdue = task.overdue ? '<span class="overdue-badge">OVERDUE</span>' : '';
            const isAssigned = task.status === 'ASSIGNED';
            const isInProgress = task.status === 'IN_PROGRESS';

            let actionHtml = '';
            if (isAssigned) {
                actionHtml = `<button class="btn btn-primary btn-sm btn-accept" data-id="${task.complaintId}">Accept Task</button>`;
            } else if (isInProgress) {
                actionHtml = `<button class="btn btn-teal btn-sm btn-resolve" data-id="${task.complaintId}">Mark as Resolved</button>`;
            }

            card.innerHTML = `
                <div class="complaint-card-header">
                    <span class="complaint-id">${escapeHtml(task.complaintId)}</span>
                    <div class="complaint-meta">
                        <span class="priority-badge priority-${task.priority}">${escapeHtml(task.priority)}</span>
                        <span class="status-badge status-${task.status}">${escapeHtml(task.statusDisplay || task.status)}</span>
                        ${isOverdue}
                    </div>
                </div>
                <div class="complaint-card-body">
                    <p><strong>${escapeHtml(task.categoryDisplay || task.category)}:</strong> ${escapeHtml(task.description)}</p>
                </div>
                <div class="complaint-card-footer">
                    <span>👤 Student: ${escapeHtml(task.studentName || 'Student')}</span>
                    <span>⏱ Due By: ${formatDate(task.dueBy)}</span>
                    <div style="display:flex; gap: 8px;">
                        <button class="btn btn-outline btn-sm btn-view-details" data-id="${task.complaintId}">Details</button>
                        ${actionHtml}
                    </div>
                </div>
            `;
            listEl.appendChild(card);
        });

        // Bind events
        listEl.querySelectorAll('.btn-view-details').forEach(btn => {
            btn.onclick = () => openComplaintModal(btn.getAttribute('data-id'));
        });

        listEl.querySelectorAll('.btn-accept').forEach(btn => {
            btn.onclick = async () => {
                const id = btn.getAttribute('data-id');
                try {
                    await apiRequest(`/staff/tasks/${id}/accept`, { method: 'POST' });
                    showToast(`Task ${id} accepted. Status: IN PROGRESS`, 'success');
                    loadStaffTasks();
                } catch (err) {
                    showToast(err.message, 'error');
                }
            };
        });

        listEl.querySelectorAll('.btn-resolve').forEach(btn => {
            btn.onclick = async () => {
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
                    loadStaffTasks();
                } catch (err) {
                    showToast(err.message, 'error');
                }
            };
        });

    } catch (err) {
        listEl.innerHTML = `<div class="toast toast-error">${escapeHtml(err.message)}</div>`;
    }
}
