/**
 * Admin Dashboard Module.
 * Manages triage board, filtering, pagination, priority override,
 * staff assignment (manual and auto-assign strategy), CSV export,
 * analytics with charts, and staff management.
 */
import { apiRequest } from './api.js';
import { showToast, escapeHtml } from './toast.js';
import { formatDate, openComplaintModal } from './student.js';

let currentPage = 0;
const pageSize = 15;
let statusChart = null;
let categoryChart = null;
let priorityChart = null;
let trendChart = null;

export function initAdminView() {
    setupTabs();
    setupFilters();
    setupCsvExport();
    loadAdminComplaints();
}

function setupTabs() {
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.onclick = () => {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');

            const tabName = tab.getAttribute('data-tab');
            document.getElementById('adminComplaintsTab').style.display = tabName === 'complaints' ? 'block' : 'none';
            document.getElementById('adminAnalyticsTab').style.display = tabName === 'analytics' ? 'block' : 'none';
            document.getElementById('adminStaffTab').style.display = tabName === 'staff' ? 'block' : 'none';

            if (tabName === 'analytics') {
                loadAnalytics();
            } else if (tabName === 'staff') {
                loadStaffList();
            } else if (tabName === 'complaints') {
                loadAdminComplaints();
            }
        };
    });
}

function setupFilters() {
    const statusSelect = document.getElementById('filterStatus');
    const catSelect = document.getElementById('filterCategory');
    const priSelect = document.getElementById('filterPriority');
    const searchInput = document.getElementById('filterSearch');

    const triggerSearch = debounce(() => {
        currentPage = 0;
        loadAdminComplaints();
    }, 300);

    if (statusSelect) statusSelect.onchange = triggerSearch;
    if (catSelect) catSelect.onchange = triggerSearch;
    if (priSelect) priSelect.onchange = triggerSearch;
    if (searchInput) searchInput.oninput = triggerSearch;
}

function setupCsvExport() {
    const exportBtn = document.getElementById('exportCsvBtn');
    if (exportBtn) {
        exportBtn.onclick = async () => {
            try {
                const csvData = await apiRequest('/admin/export.csv');
                const blob = new Blob([csvData], { type: 'text/csv;charset=utf-8;' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `hcms_complaints_${new Date().toISOString().slice(0, 10)}.csv`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                showToast('CSV Export downloaded', 'success');
            } catch (err) {
                showToast('Export failed: ' + err.message, 'error');
            }
        };
    }
}

export async function loadAdminComplaints() {
    const wrapper = document.getElementById('adminComplaints');
    const paginationEl = document.getElementById('adminPagination');
    if (!wrapper) return;

    wrapper.innerHTML = '<div class="loading">Loading triage queue...</div>';

    const status = document.getElementById('filterStatus')?.value || '';
    const category = document.getElementById('filterCategory')?.value || '';
    const priority = document.getElementById('filterPriority')?.value || '';
    const search = document.getElementById('filterSearch')?.value.trim() || '';

    const params = new URLSearchParams({
        page: currentPage,
        size: pageSize
    });
    if (status) params.append('status', status);
    if (category) params.append('category', category);
    if (priority) params.append('priority', priority);
    if (search) params.append('q', search);

    try {
        const data = await apiRequest(`/admin/complaints?${params.toString()}`);
        const complaints = data.complaints || [];
        const total = data.total || 0;

        if (complaints.length === 0) {
            wrapper.innerHTML = `
                <div class="empty-state">
                    <p>No complaints match current filters.</p>
                </div>
            `;
            if (paginationEl) paginationEl.innerHTML = '';
            return;
        }

        let tableHtml = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Priority</th>
                        <th>Category</th>
                        <th>Description</th>
                        <th>Student / Room</th>
                        <th>Status</th>
                        <th>Staff</th>
                        <th>Due By</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
        `;

        complaints.forEach(c => {
            const isOverdue = c.overdue ? '<span class="overdue-badge">OVERDUE</span>' : '';
            const location = `${escapeHtml(c.studentName || 'Student')} (Blk ${escapeHtml(c.hostelBlock || '-')}, R${escapeHtml(c.roomNumber || '-')})`;
            
            tableHtml += `
                <tr>
                    <td><a href="#" class="complaint-link" data-id="${c.complaintId}"><strong>${escapeHtml(c.complaintId)}</strong></a></td>
                    <td>
                        <select class="priority-select" data-id="${c.complaintId}" style="font-size:0.8rem; font-weight:700; padding:2px 4px; border:1.5px solid #0B1F4A;">
                            <option value="1" ${c.priority === 'HIGH' ? 'selected' : ''}>🔴 High</option>
                            <option value="2" ${c.priority === 'MEDIUM' ? 'selected' : ''}>🟠 Medium</option>
                            <option value="3" ${c.priority === 'LOW' ? 'selected' : ''}>🟢 Low</option>
                        </select>
                    </td>
                    <td>${escapeHtml(c.categoryDisplay || c.category)}</td>
                    <td style="max-width:240px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;" title="${escapeHtml(c.description)}">
                        ${escapeHtml(c.description)}
                    </td>
                    <td>${location}</td>
                    <td>
                        <span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>
                        ${isOverdue}
                    </td>
                    <td>${escapeHtml(c.staffName || 'Unassigned')}</td>
                    <td>${formatDate(c.dueBy)}</td>
                    <td>
                        <div style="display:flex; gap:6px;">
                            <button class="btn btn-outline btn-sm btn-assign" data-id="${c.complaintId}" data-cat="${c.category}" title="Assign Staff">👤 Assign</button>
                            <button class="btn btn-primary btn-sm btn-auto" data-id="${c.complaintId}" title="Auto-Assign using Strategy">⚡ Auto</button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tableHtml += '</tbody></table>';
        wrapper.innerHTML = tableHtml;

        // Bind table action clicks
        wrapper.querySelectorAll('.complaint-link').forEach(link => {
            link.onclick = (e) => {
                e.preventDefault();
                openComplaintModal(link.getAttribute('data-id'));
            };
        });

        wrapper.querySelectorAll('.priority-select').forEach(sel => {
            sel.onchange = async () => {
                const id = sel.getAttribute('data-id');
                const newPriority = parseInt(sel.value, 10);
                try {
                    await apiRequest(`/admin/complaints/${id}/priority`, {
                        method: 'PUT',
                        body: { priority: newPriority }
                    });
                    showToast(`Priority updated for ${id}`, 'success');
                } catch (err) {
                    showToast(err.message, 'error');
                    loadAdminComplaints();
                }
            };
        });

        wrapper.querySelectorAll('.btn-assign').forEach(btn => {
            btn.onclick = () => {
                const id = btn.getAttribute('data-id');
                const cat = btn.getAttribute('data-cat');
                openAssignModal(id, cat);
            };
        });

        wrapper.querySelectorAll('.btn-auto').forEach(btn => {
            btn.onclick = async () => {
                const id = btn.getAttribute('data-id');
                try {
                    const res = await apiRequest(`/admin/complaints/${id}/auto-assign`, { method: 'POST' });
                    showToast(`Complaint ${id} auto-assigned successfully!`, 'success');
                    loadAdminComplaints();
                } catch (err) {
                    showToast(err.message, 'error');
                }
            };
        });

        // Render Pagination
        renderPagination(total);

    } catch (err) {
        wrapper.innerHTML = `<div class="toast toast-error">${escapeHtml(err.message)}</div>`;
    }
}

function renderPagination(total) {
    const el = document.getElementById('adminPagination');
    if (!el) return;

    const totalPages = Math.ceil(total / pageSize);
    if (totalPages <= 1) {
        el.innerHTML = '';
        return;
    }

    let html = '';
    if (currentPage > 0) {
        html += `<button class="btn btn-outline btn-sm" id="prevPage">◀ Prev</button>`;
    }
    html += `<span style="font-family: monospace; align-self: center; font-weight: 700; margin: 0 8px;">Page ${currentPage + 1} of ${totalPages} (${total} total)</span>`;
    if (currentPage < totalPages - 1) {
        html += `<button class="btn btn-outline btn-sm" id="nextPage">Next ▶</button>`;
    }

    el.innerHTML = html;

    const prev = document.getElementById('prevPage');
    if (prev) prev.onclick = () => { currentPage--; loadAdminComplaints(); };

    const next = document.getElementById('nextPage');
    if (next) next.onclick = () => { currentPage++; loadAdminComplaints(); };
}

export async function openAssignModal(complaintId, category) {
    const modal = document.getElementById('assignModal');
    const staffListEl = document.getElementById('assignStaffList');
    const autoBtn = document.getElementById('autoAssignBtn');
    const closeBtn = document.getElementById('closeAssignModal');

    if (!modal || !staffListEl) return;

    modal.style.display = 'flex';
    staffListEl.innerHTML = '<div class="loading">Fetching staff members...</div>';

    closeBtn.onclick = () => { modal.style.display = 'none'; };
    modal.querySelector('.modal-overlay').onclick = () => { modal.style.display = 'none'; };

    if (autoBtn) {
        autoBtn.onclick = async () => {
            try {
                await apiRequest(`/admin/complaints/${complaintId}/auto-assign`, { method: 'POST' });
                showToast('Auto-assigned successfully!', 'success');
                modal.style.display = 'none';
                loadAdminComplaints();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    try {
        const data = await apiRequest('/admin/staff');
        const staff = (data && data.staff) || [];

        if (staff.length === 0) {
            staffListEl.innerHTML = '<p>No staff available.</p>';
            return;
        }

        let html = '<div style="display:flex; flex-direction:column; gap:8px; margin: 15px 0;">';
        staff.forEach(s => {
            const isMatch = s.specialization === category;
            const matchBadge = isMatch ? '<span style="background:#CCFBF1; color:#115E59; padding:2px 6px; font-size:0.75rem; border:1px solid #1B7A6B; font-weight:800;">MATCH</span>' : '';
            const statusBadge = s.available ? '<span style="color:#1B7A6B;">● Available</span>' : '<span style="color:#D90429;">● Busy</span>';

            html += `
                <div style="display:flex; justify-content:space-between; align-items:center; border:2px solid #0B1F4A; padding:8px 12px; background: ${isMatch ? '#eefbf8' : '#fff'};">
                    <div>
                        <strong>${escapeHtml(s.name)}</strong> (${escapeHtml(s.specializationDisplay || s.specialization)}) ${matchBadge}
                        <div style="font-size:0.8rem; font-family:monospace;">Tasks: ${s.activeComplaints} | Rating: ★ ${s.avgRating || '—'} | ${statusBadge}</div>
                    </div>
                    <button class="btn btn-teal btn-sm btn-assign-manual" data-staff-id="${s.userId}">Assign</button>
                </div>
            `;
        });
        html += '</div>';
        staffListEl.innerHTML = html;

        staffListEl.querySelectorAll('.btn-assign-manual').forEach(btn => {
            btn.onclick = async () => {
                const staffId = parseInt(btn.getAttribute('data-staff-id'), 10);
                try {
                    await apiRequest(`/admin/complaints/${complaintId}/assign`, {
                        method: 'POST',
                        body: { staffId }
                    });
                    showToast('Assigned successfully!', 'success');
                    modal.style.display = 'none';
                    loadAdminComplaints();
                } catch (err) {
                    showToast(err.message, 'error');
                }
            };
        });

    } catch (err) {
        staffListEl.innerHTML = `<div class="toast toast-error">${escapeHtml(err.message)}</div>`;
    }
}

export async function loadAnalytics() {
    try {
        const stats = await apiRequest('/admin/analytics');

        // Overview stats
        document.getElementById('statTotal').textContent = stats.totalComplaints || 0;
        document.getElementById('statAvgRes').textContent = stats.avgResolutionHours ? `${stats.avgResolutionHours} hrs` : 'N/A';
        document.getElementById('statBreach').textContent = `${stats.slaBreachRatePercent || 0}%`;

        // Render Status Chart
        if (window.Chart) {
            renderStatusChart(stats.byStatus || {});
            renderCategoryChart(stats.byCategory || {});
            renderPriorityChart(stats.byPriority || {});
            renderTrendChart(stats.trend30Days || {});
        }

        // Staff Performance Table
        const tableBody = document.querySelector('#staffSummaryTable tbody');
        if (tableBody && stats.staffPerformance) {
            let sHtml = '';
            stats.staffPerformance.forEach(s => {
                sHtml += `
                    <tr>
                        <td><strong>${escapeHtml(s.name)}</strong></td>
                        <td>${escapeHtml(s.specialization)}</td>
                        <td>${s.activeTasks}</td>
                        <td>★ ${s.avgRating ? s.avgRating : '—'}</td>
                        <td>${s.available ? '<span style="color:#1B7A6B; font-weight:800;">Available</span>' : '<span style="color:#D90429; font-weight:800;">Busy</span>'}</td>
                    </tr>
                `;
            });
            tableBody.innerHTML = sHtml;
        }

    } catch (err) {
        showToast('Failed to load analytics: ' + err.message, 'error');
    }
}

function renderStatusChart(byStatus) {
    const canvas = document.getElementById('chartStatus');
    if (!canvas) return;
    if (statusChart) statusChart.destroy();

    const labels = Object.keys(byStatus);
    const data = Object.values(byStatus);

    statusChart = new window.Chart(canvas, {
        type: 'doughnut',
        data: {
            labels,
            datasets: [{
                data,
                backgroundColor: ['#6C757D', '#0B1F4A', '#E8960C', '#1B7A6B', '#2B3A42', '#D9480F']
            }]
        }
    });
}

function renderCategoryChart(byCategory) {
    const canvas = document.getElementById('chartCategory');
    if (!canvas) return;
    if (categoryChart) categoryChart.destroy();

    const labels = Object.keys(byCategory);
    const data = Object.values(byCategory);

    categoryChart = new window.Chart(canvas, {
        type: 'bar',
        data: {
            labels,
            datasets: [{
                data,
                backgroundColor: '#00B4D8'
            }]
        }
    });
}

function renderPriorityChart(byPriority) {
    const canvas = document.getElementById('chartPriority');
    if (!canvas) return;
    if (priorityChart) priorityChart.destroy();

    const labels = Object.keys(byPriority);
    const data = Object.values(byPriority);

    priorityChart = new window.Chart(canvas, {
        type: 'doughnut',
        data: {
            labels,
            datasets: [{
                data,
                backgroundColor: ['#D90429', '#E8960C', '#1B7A6B']
            }]
        }
    });
}

function renderTrendChart(trend30Days) {
    const canvas = document.getElementById('chartTrend');
    if (!canvas) return;
    if (trendChart) trendChart.destroy();

    const labels = Object.keys(trend30Days);
    const data = Object.values(trend30Days);

    trendChart = new window.Chart(canvas, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                data,
                borderColor: '#0B1F4A',
                backgroundColor: 'rgba(0, 180, 216, 0.25)'
            }]
        }
    });
}

export async function loadStaffList() {
    const grid = document.getElementById('staffList');
    if (!grid) return;

    grid.innerHTML = '<div class="loading">Loading staff directory...</div>';

    try {
        const data = await apiRequest('/admin/staff');
        const staff = (data && data.staff) || [];

        let html = '';
        staff.forEach(s => {
            html += `
                <div class="staff-card">
                    <div class="staff-card-header">
                        <h4>${escapeHtml(s.name)}</h4>
                        <span class="status-badge" style="background:${s.available ? '#D1FAE5' : '#FEE2E2'}">${s.available ? 'AVAILABLE' : 'BUSY'}</span>
                    </div>
                    <p><strong>Email:</strong> ${escapeHtml(s.email)}</p>
                    <p><strong>Specialization:</strong> ${escapeHtml(s.specializationDisplay || s.specialization)}</p>
                    <p><strong>Active Complaints:</strong> ${s.activeComplaints}</p>
                    <p><strong>Average Rating:</strong> ★ ${s.avgRating || '—'}</p>
                </div>
            `;
        });

        grid.innerHTML = html;
    } catch (err) {
        grid.innerHTML = `<div class="toast toast-error">${escapeHtml(err.message)}</div>`;
    }
}

function debounce(fn, ms) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), ms);
    };
}
