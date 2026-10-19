/**
 * Admin Dashboard Module.
 * Manages triage board, filtering, pagination, priority override,
 * staff assignment (manual and auto-assign strategy), CSV export,
 * analytics with charts, and staff management.
 */
import { apiRequest } from './api.js';
import { showToast, escapeHtml } from './toast.js';
import { openComplaintModal } from './student.js';
import { formatExactDate, formatRelativeDate, formatDueStatus } from './dates.js';
import { getIcon } from './icons.js';

let currentSubView = 'triage';

const triageState = {
    page: 0,
    size: 25,
    counter: '',
    q: '',
    status: '',
    category: '',
    priority: '',
    hostelBlock: '',
    fromDate: '',
    toDate: ''
};

const allState = {
    page: 0,
    size: 25,
    q: '',
    status: '',
    category: '',
    priority: '',
    hostelBlock: '',
    fromDate: '',
    toDate: ''
};

let statusChart = null;
let categoryChart = null;
let priorityChart = null;
let trendChart = null;

export function initAdminView(subroute = 'triage') {
    currentSubView = subroute;
    setupTriageToolbar();
    setupAllComplaintsToolbar();
    setupCsvExport();
    showAdminSubView(subroute);
}

export function showAdminSubView(subroute) {
    currentSubView = subroute;
    const triageTab = document.getElementById('adminTriageTab');
    const complaintsTab = document.getElementById('adminComplaintsTab');
    const analyticsTab = document.getElementById('adminAnalyticsTab');
    const staffTab = document.getElementById('adminStaffTab');

    if (triageTab) triageTab.style.display = subroute === 'triage' ? 'block' : 'none';
    if (complaintsTab) complaintsTab.style.display = subroute === 'complaints' ? 'block' : 'none';
    if (analyticsTab) analyticsTab.style.display = subroute === 'analytics' ? 'block' : 'none';
    if (staffTab) staffTab.style.display = subroute === 'staff' ? 'block' : 'none';

    if (subroute === 'analytics') {
        loadAnalytics();
    } else if (subroute === 'staff') {
        loadStaffList();
    } else if (subroute === 'complaints') {
        loadAdminComplaints();
    } else if (subroute === 'triage') {
        loadTriageBoard();
    }
}

function setupTriageToolbar() {
    // 4 Clickable Counter Cards
    const cards = document.querySelectorAll('#triageCountersGrid .triage-counter-card');
    cards.forEach(card => {
        card.onclick = () => {
            const counterType = card.getAttribute('data-counter');
            if (triageState.counter === counterType) {
                triageState.counter = '';
                card.classList.remove('active');
                card.setAttribute('aria-pressed', 'false');
            } else {
                triageState.counter = counterType;
                cards.forEach(c => {
                    c.classList.remove('active');
                    c.setAttribute('aria-pressed', 'false');
                });
                card.classList.add('active');
                card.setAttribute('aria-pressed', 'true');
            }
            triageState.page = 0;
            updateTriageChips();
            loadTriageBoard();
        };
    });

    // Search input
    const searchInput = document.getElementById('triageSearchInput');
    if (searchInput) {
        searchInput.oninput = debounce(() => {
            triageState.q = searchInput.value.trim();
            triageState.page = 0;
            updateTriageChips();
            loadTriageBoard();
        }, 300);
    }

    // Filters Popover
    const filterBtn = document.getElementById('triageFiltersBtn');
    const popover = document.getElementById('triageFiltersPopover');
    const applyBtn = document.getElementById('triagePopApplyBtn');
    const resetBtn = document.getElementById('triagePopResetBtn');

    if (filterBtn && popover) {
        filterBtn.onclick = (e) => {
            e.stopPropagation();
            const isOpen = popover.style.display === 'block';
            popover.style.display = isOpen ? 'none' : 'block';
            filterBtn.setAttribute('aria-expanded', !isOpen);
        };

        // Close on click outside
        document.addEventListener('click', (e) => {
            if (popover.style.display === 'block' && !popover.contains(e.target) && e.target !== filterBtn) {
                popover.style.display = 'none';
                filterBtn.setAttribute('aria-expanded', 'false');
            }
        });

        if (applyBtn) {
            applyBtn.onclick = () => {
                triageState.status = document.getElementById('triagePopStatus')?.value || '';
                triageState.category = document.getElementById('triagePopCategory')?.value || '';
                triageState.priority = document.getElementById('triagePopPriority')?.value || '';
                triageState.hostelBlock = document.getElementById('triagePopBlock')?.value || '';
                triageState.fromDate = document.getElementById('triagePopFromDate')?.value || '';
                triageState.toDate = document.getElementById('triagePopToDate')?.value || '';
                triageState.page = 0;
                popover.style.display = 'none';
                filterBtn.setAttribute('aria-expanded', 'false');
                updateTriageChips();
                loadTriageBoard();
            };
        }

        if (resetBtn) {
            resetBtn.onclick = () => {
                if (document.getElementById('triagePopStatus')) document.getElementById('triagePopStatus').value = '';
                if (document.getElementById('triagePopCategory')) document.getElementById('triagePopCategory').value = '';
                if (document.getElementById('triagePopPriority')) document.getElementById('triagePopPriority').value = '';
                if (document.getElementById('triagePopBlock')) document.getElementById('triagePopBlock').value = '';
                if (document.getElementById('triagePopFromDate')) document.getElementById('triagePopFromDate').value = '';
                if (document.getElementById('triagePopToDate')) document.getElementById('triagePopToDate').value = '';
            };
        }
    }
}

function updateTriageChips() {
    const chipsEl = document.getElementById('triageActiveChips');
    const badgeEl = document.getElementById('triageFilterBadge');
    if (!chipsEl) return;

    let filterCount = 0;
    const chips = [];

    if (triageState.counter) {
        filterCount++;
        const label = triageState.counter.replace('_', ' ').toUpperCase();
        chips.push(`<span class="filter-chip">Queue: ${escapeHtml(label)} <button class="chip-remove" data-clear="counter" aria-label="Clear counter filter">✕</button></span>`);
    }
    if (triageState.status) {
        filterCount++;
        chips.push(`<span class="filter-chip">Status: ${escapeHtml(triageState.status)} <button class="chip-remove" data-clear="status" aria-label="Clear status filter">✕</button></span>`);
    }
    if (triageState.category) {
        filterCount++;
        chips.push(`<span class="filter-chip">Category: ${escapeHtml(triageState.category)} <button class="chip-remove" data-clear="category" aria-label="Clear category filter">✕</button></span>`);
    }
    if (triageState.priority) {
        filterCount++;
        const pLabel = triageState.priority === '1' ? 'High' : triageState.priority === '2' ? 'Medium' : triageState.priority === '3' ? 'Low' : triageState.priority;
        chips.push(`<span class="filter-chip">Priority: ${escapeHtml(pLabel)} <button class="chip-remove" data-clear="priority" aria-label="Clear priority filter">✕</button></span>`);
    }
    if (triageState.hostelBlock) {
        filterCount++;
        chips.push(`<span class="filter-chip">Block: ${escapeHtml(triageState.hostelBlock)} <button class="chip-remove" data-clear="hostelBlock" aria-label="Clear block filter">✕</button></span>`);
    }
    if (triageState.fromDate) {
        filterCount++;
        chips.push(`<span class="filter-chip">From: ${escapeHtml(triageState.fromDate)} <button class="chip-remove" data-clear="fromDate" aria-label="Clear from date">✕</button></span>`);
    }
    if (triageState.toDate) {
        filterCount++;
        chips.push(`<span class="filter-chip">To: ${escapeHtml(triageState.toDate)} <button class="chip-remove" data-clear="toDate" aria-label="Clear to date">✕</button></span>`);
    }
    if (triageState.q) {
        chips.push(`<span class="filter-chip">Search: "${escapeHtml(triageState.q)}" <button class="chip-remove" data-clear="q" aria-label="Clear search">✕</button></span>`);
    }

    if (badgeEl) {
        badgeEl.textContent = filterCount;
        badgeEl.style.display = filterCount > 0 ? 'inline-flex' : 'none';
    }

    if (chips.length > 0) {
        chips.push(`<button class="chip-clear-all" id="triageClearAllChips" type="button">Clear All</button>`);
        chipsEl.innerHTML = chips.join('');

        chipsEl.querySelectorAll('.chip-remove').forEach(btn => {
            btn.onclick = () => {
                const key = btn.getAttribute('data-clear');
                if (key === 'counter') {
                    triageState.counter = '';
                    document.querySelectorAll('#triageCountersGrid .triage-counter-card').forEach(c => {
                        c.classList.remove('active');
                        c.setAttribute('aria-pressed', 'false');
                    });
                } else if (key === 'q') {
                    triageState.q = '';
                    const searchInput = document.getElementById('triageSearchInput');
                    if (searchInput) searchInput.value = '';
                } else {
                    triageState[key] = '';
                    // Reset field in popover
                    const popMap = {
                        status: 'triagePopStatus',
                        category: 'triagePopCategory',
                        priority: 'triagePopPriority',
                        hostelBlock: 'triagePopBlock',
                        fromDate: 'triagePopFromDate',
                        toDate: 'triagePopToDate'
                    };
                    const elId = popMap[key];
                    if (elId && document.getElementById(elId)) document.getElementById(elId).value = '';
                }
                triageState.page = 0;
                updateTriageChips();
                loadTriageBoard();
            };
        });

        const clearAllBtn = document.getElementById('triageClearAllChips');
        if (clearAllBtn) {
            clearAllBtn.onclick = () => {
                triageState.counter = '';
                triageState.q = '';
                triageState.status = '';
                triageState.category = '';
                triageState.priority = '';
                triageState.hostelBlock = '';
                triageState.fromDate = '';
                triageState.toDate = '';
                triageState.page = 0;

                const searchInput = document.getElementById('triageSearchInput');
                if (searchInput) searchInput.value = '';
                document.querySelectorAll('#triageCountersGrid .triage-counter-card').forEach(c => {
                    c.classList.remove('active');
                    c.setAttribute('aria-pressed', 'false');
                });
                ['triagePopStatus', 'triagePopCategory', 'triagePopPriority', 'triagePopBlock', 'triagePopFromDate', 'triagePopToDate'].forEach(id => {
                    const el = document.getElementById(id);
                    if (el) el.value = '';
                });
                updateTriageChips();
                loadTriageBoard();
            };
        }
    } else {
        chipsEl.innerHTML = '';
    }
}

function setupAllComplaintsToolbar() {
    const searchInput = document.getElementById('allSearchInput');
    if (searchInput) {
        searchInput.oninput = debounce(() => {
            allState.q = searchInput.value.trim();
            allState.page = 0;
            updateAllChips();
            loadAdminComplaints();
        }, 300);
    }

    const filterBtn = document.getElementById('allFiltersBtn');
    const popover = document.getElementById('allFiltersPopover');
    const applyBtn = document.getElementById('allPopApplyBtn');
    const resetBtn = document.getElementById('allPopResetBtn');

    if (filterBtn && popover) {
        filterBtn.onclick = (e) => {
            e.stopPropagation();
            const isOpen = popover.style.display === 'block';
            popover.style.display = isOpen ? 'none' : 'block';
            filterBtn.setAttribute('aria-expanded', !isOpen);
        };

        document.addEventListener('click', (e) => {
            if (popover.style.display === 'block' && !popover.contains(e.target) && e.target !== filterBtn) {
                popover.style.display = 'none';
                filterBtn.setAttribute('aria-expanded', 'false');
            }
        });

        if (applyBtn) {
            applyBtn.onclick = () => {
                allState.status = document.getElementById('allPopStatus')?.value || '';
                allState.category = document.getElementById('allPopCategory')?.value || '';
                allState.priority = document.getElementById('allPopPriority')?.value || '';
                allState.hostelBlock = document.getElementById('allPopBlock')?.value || '';
                allState.fromDate = document.getElementById('allPopFromDate')?.value || '';
                allState.toDate = document.getElementById('allPopToDate')?.value || '';
                allState.page = 0;
                popover.style.display = 'none';
                filterBtn.setAttribute('aria-expanded', 'false');
                updateAllChips();
                loadAdminComplaints();
            };
        }

        if (resetBtn) {
            resetBtn.onclick = () => {
                ['allPopStatus', 'allPopCategory', 'allPopPriority', 'allPopBlock', 'allPopFromDate', 'allPopToDate'].forEach(id => {
                    const el = document.getElementById(id);
                    if (el) el.value = '';
                });
            };
        }
    }
}

function updateAllChips() {
    const chipsEl = document.getElementById('allActiveChips');
    const badgeEl = document.getElementById('allFilterBadge');
    if (!chipsEl) return;

    let filterCount = 0;
    const chips = [];

    if (allState.status) {
        filterCount++;
        chips.push(`<span class="filter-chip">Status: ${escapeHtml(allState.status)} <button class="chip-remove" data-clear="status">✕</button></span>`);
    }
    if (allState.category) {
        filterCount++;
        chips.push(`<span class="filter-chip">Category: ${escapeHtml(allState.category)} <button class="chip-remove" data-clear="category">✕</button></span>`);
    }
    if (allState.priority) {
        filterCount++;
        const pLabel = allState.priority === '1' ? 'High' : allState.priority === '2' ? 'Medium' : allState.priority === '3' ? 'Low' : allState.priority;
        chips.push(`<span class="filter-chip">Priority: ${escapeHtml(pLabel)} <button class="chip-remove" data-clear="priority">✕</button></span>`);
    }
    if (allState.hostelBlock) {
        filterCount++;
        chips.push(`<span class="filter-chip">Block: ${escapeHtml(allState.hostelBlock)} <button class="chip-remove" data-clear="hostelBlock">✕</button></span>`);
    }
    if (allState.fromDate) {
        filterCount++;
        chips.push(`<span class="filter-chip">From: ${escapeHtml(allState.fromDate)} <button class="chip-remove" data-clear="fromDate">✕</button></span>`);
    }
    if (allState.toDate) {
        filterCount++;
        chips.push(`<span class="filter-chip">To: ${escapeHtml(allState.toDate)} <button class="chip-remove" data-clear="toDate">✕</button></span>`);
    }
    if (allState.q) {
        chips.push(`<span class="filter-chip">Search: "${escapeHtml(allState.q)}" <button class="chip-remove" data-clear="q">✕</button></span>`);
    }

    if (badgeEl) {
        badgeEl.textContent = filterCount;
        badgeEl.style.display = filterCount > 0 ? 'inline-flex' : 'none';
    }

    if (chips.length > 0) {
        chips.push(`<button class="chip-clear-all" id="allClearAllChips" type="button">Clear All</button>`);
        chipsEl.innerHTML = chips.join('');

        chipsEl.querySelectorAll('.chip-remove').forEach(btn => {
            btn.onclick = () => {
                const key = btn.getAttribute('data-clear');
                if (key === 'q') {
                    allState.q = '';
                    const searchInput = document.getElementById('allSearchInput');
                    if (searchInput) searchInput.value = '';
                } else {
                    allState[key] = '';
                    const popMap = {
                        status: 'allPopStatus',
                        category: 'allPopCategory',
                        priority: 'allPopPriority',
                        hostelBlock: 'allPopBlock',
                        fromDate: 'allPopFromDate',
                        toDate: 'allPopToDate'
                    };
                    const elId = popMap[key];
                    if (elId && document.getElementById(elId)) document.getElementById(elId).value = '';
                }
                allState.page = 0;
                updateAllChips();
                loadAdminComplaints();
            };
        });

        const clearAllBtn = document.getElementById('allClearAllChips');
        if (clearAllBtn) {
            clearAllBtn.onclick = () => {
                allState.q = '';
                allState.status = '';
                allState.category = '';
                allState.priority = '';
                allState.hostelBlock = '';
                allState.fromDate = '';
                allState.toDate = '';
                allState.page = 0;

                const searchInput = document.getElementById('allSearchInput');
                if (searchInput) searchInput.value = '';
                ['allPopStatus', 'allPopCategory', 'allPopPriority', 'allPopBlock', 'allPopFromDate', 'allPopToDate'].forEach(id => {
                    const el = document.getElementById(id);
                    if (el) el.value = '';
                });
                updateAllChips();
                loadAdminComplaints();
            };
        }
    } else {
        chipsEl.innerHTML = '';
    }
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
                showToast('Complaints CSV exported successfully', 'success');
            } catch (err) {
                showToast('Export failed: ' + err.message, 'error');
            }
        };
    }

    const exportAuditBtn = document.getElementById('exportAuditCsvBtn');
    if (exportAuditBtn) {
        exportAuditBtn.onclick = async () => {
            try {
                const csvData = await apiRequest('/admin/export-audit.csv');
                const blob = new Blob([csvData], { type: 'text/csv;charset=utf-8;' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `hcms_audit_history_${new Date().toISOString().slice(0, 10)}.csv`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                showToast('Audit History CSV exported successfully', 'success');
            } catch (err) {
                showToast('Audit export failed: ' + err.message, 'error');
            }
        };
    }
}

function renderComplaintsTable(complaints, wrapper, onRefresh) {
    if (!complaints || complaints.length === 0) {
        wrapper.innerHTML = `
            <div class="empty-state" style="padding: 40px 20px; text-align: center;">
                <div class="empty-icon" style="margin-bottom: 12px; color: var(--grey);">${getIcon('inbox', '', 40)}</div>
                <h3 style="margin-bottom: 6px;">No complaints found</h3>
                <p style="color: var(--grey); margin-bottom: 16px;">There are no complaints matching your current filters.</p>
            </div>
        `;
        return;
    }

    let html = `
        <div class="table-responsive">
            <table class="data-table">
                <thead>
                    <tr>
                        <th style="white-space: nowrap;">ID</th>
                        <th>Priority</th>
                        <th>Category</th>
                        <th>Description</th>
                        <th>Location</th>
                        <th>Status</th>
                        <th>Assigned Staff</th>
                        <th>Due Date</th>
                        <th style="text-align: right;">Actions</th>
                    </tr>
                </thead>
                <tbody>
    `;

    complaints.forEach(c => {
        const pLabel = c.priority === 1 ? 'High' : c.priority === 2 ? 'Medium' : 'Low';
        const pClass = c.priority === 1 ? 'priority-high' : c.priority === 2 ? 'priority-medium' : 'priority-low';
        const location = `${escapeHtml(c.hostelBlock || '')} - ${escapeHtml(c.roomNumber || '')}`;
        const isOverdue = c.overdue || (c.dueBy && new Date(c.dueBy) < new Date() && c.status !== 'RESOLVED' && c.status !== 'CLOSED');
        
        let staffDisplay = '<span style="color:var(--grey); font-style:italic;">Unassigned</span>';
        if (c.staffName || c.assignedStaffName) {
            const name = c.staffName || c.assignedStaffName;
            const spec = c.assignedStaffSpecialization || c.specializationDisplay || '';
            staffDisplay = `<strong>${escapeHtml(name)}</strong>${spec ? ` <span style="font-size:0.8rem; color:var(--grey);">(${escapeHtml(spec)})</span>` : ''}`;
        }

        // Contextual action buttons per Phase 3:
        // - NEW and unassigned: primary "Assign" and secondary "Auto-assign"
        // - ASSIGNED or IN_PROGRESS: small "Reassign" link only
        // - REOPENED: highlighted "Assign" (needs attention)
        // - RESOLVED or CLOSED: no assignment actions
        let actionsHtml = '';
        if (c.status === 'NEW' && (!c.assignedStaffId && !c.staffId)) {
            actionsHtml = `
                <div style="display:flex; gap:6px; justify-content:flex-end;">
                    <button class="btn btn-primary btn-sm btn-assign" data-id="${c.complaintId}" data-cat="${c.category}">Assign</button>
                    <button class="btn btn-outline btn-sm btn-auto" data-id="${c.complaintId}">Auto</button>
                </div>
            `;
        } else if (c.status === 'REOPENED') {
            actionsHtml = `
                <div style="display:flex; gap:6px; justify-content:flex-end;">
                    <button class="btn btn-amber btn-sm btn-assign" data-id="${c.complaintId}" data-cat="${c.category}">Assign</button>
                </div>
            `;
        } else if (c.status === 'ASSIGNED' || c.status === 'IN_PROGRESS') {
            actionsHtml = `
                <div style="display:flex; gap:6px; justify-content:flex-end;">
                    <button class="btn btn-link btn-sm btn-assign" data-id="${c.complaintId}" data-cat="${c.category}" style="text-decoration:underline; font-size:0.85rem; padding: 2px 4px;">Reassign</button>
                </div>
            `;
        } else {
            actionsHtml = `<span style="color:var(--grey); font-size:0.85rem;">—</span>`;
        }

        html += `
            <tr class="complaint-row" data-id="${c.complaintId}" style="cursor: pointer;">
                <td data-label="ID" style="white-space: nowrap;">
                    <a href="#" class="complaint-link font-mono" data-id="${c.complaintId}">${escapeHtml(c.complaintId)}</a>
                </td>
                <td data-label="Priority">
                    <span class="badge ${pClass}">${escapeHtml(pLabel)}</span>
                </td>
                <td data-label="Category">${escapeHtml(c.categoryDisplay || c.category)}</td>
                <td data-label="Description" style="max-width:240px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;" title="${escapeHtml(c.description)}">
                    ${escapeHtml(c.description)}
                </td>
                <td data-label="Location">${location}</td>
                <td data-label="Status" style="white-space: nowrap;">
                    <span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>
                    ${isOverdue ? '<span class="badge badge-overdue" style="margin-left:4px;">OVERDUE</span>' : ''}
                </td>
                <td data-label="Assigned Staff">${staffDisplay}</td>
                <td data-label="Due Date">
                    <span title="${formatExactDate(c.dueBy)}">${formatDueStatus(c.dueBy, c.status)}</span>
                </td>
                <td data-label="Actions" style="text-align: right;">${actionsHtml}</td>
            </tr>
        `;
    });

    html += `
                </tbody>
            </table>
        </div>
    `;

    wrapper.innerHTML = html;

    // Attach listeners
    wrapper.querySelectorAll('.complaint-row').forEach(row => {
        row.addEventListener('click', (e) => {
            if (e.target.closest('.btn-assign') || e.target.closest('.btn-auto')) return;
            e.preventDefault();
            const id = row.getAttribute('data-id');
            openComplaintModal(id);
        });
    });

    wrapper.querySelectorAll('.btn-assign').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const id = btn.getAttribute('data-id');
            const cat = btn.getAttribute('data-cat');
            openAssignModal(id, cat, onRefresh);
        });
    });

    wrapper.querySelectorAll('.btn-auto').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            e.stopPropagation();
            const id = btn.getAttribute('data-id');
            try {
                await apiRequest(`/admin/complaints/${id}/auto-assign`, { method: 'POST' });
                showToast(`Complaint ${id} auto-assigned successfully!`, 'success');
                if (typeof onRefresh === 'function') onRefresh();
            } catch (err) {
                showToast(err.message, 'error');
            }
        });
    });
}

function renderPagination(total, state, containerId, onPageChange) {
    const el = document.getElementById(containerId);
    if (!el) return;

    const totalPages = Math.ceil(total / state.size);
    if (totalPages <= 1) {
        el.innerHTML = '';
        return;
    }

    let html = '<div class="pagination-bar" style="display:flex; justify-content:center; align-items:center; gap:8px; margin-top:16px;">';
    if (state.page > 0) {
        html += `<button class="btn btn-outline btn-sm" id="${containerId}_prev" type="button">${getIcon('chevron-left', '', 14)} Prev</button>`;
    } else {
        html += `<button class="btn btn-outline btn-sm" disabled type="button">${getIcon('chevron-left', '', 14)} Prev</button>`;
    }

    html += `<span style="font-family: monospace; font-size: 0.9rem; font-weight: 700; margin: 0 8px;">Page ${state.page + 1} of ${totalPages} (${total} total)</span>`;

    if (state.page < totalPages - 1) {
        html += `<button class="btn btn-outline btn-sm" id="${containerId}_next" type="button">Next ${getIcon('chevron-right', '', 14)}</button>`;
    } else {
        html += `<button class="btn btn-outline btn-sm" disabled type="button">Next ${getIcon('chevron-right', '', 14)}</button>`;
    }
    html += '</div>';

    el.innerHTML = html;

    const prevBtn = document.getElementById(`${containerId}_prev`);
    if (prevBtn) {
        prevBtn.onclick = () => {
            state.page--;
            onPageChange();
        };
    }
    const nextBtn = document.getElementById(`${containerId}_next`);
    if (nextBtn) {
        nextBtn.onclick = () => {
            state.page++;
            onPageChange();
        };
    }
}

export async function loadTriageBoard() {
    const wrapper = document.getElementById('triageComplaints');
    if (!wrapper) return;

    wrapper.innerHTML = `
        <div class="skeleton-table" aria-busy="true" aria-label="Loading content">
            <div class="skeleton-row" style="height:44px; margin-bottom:8px; border-radius:4px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
            <div class="skeleton-row" style="height:40px; margin-bottom:6px; border-radius:4px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
            <div class="skeleton-row" style="height:40px; margin-bottom:6px; border-radius:4px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
            <div class="skeleton-row" style="height:40px; margin-bottom:6px; border-radius:4px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
        </div>
    `;

    const params = new URLSearchParams({
        page: triageState.page,
        size: triageState.size
    });
    if (triageState.counter) params.append('counter', triageState.counter);
    if (triageState.status) params.append('status', triageState.status);
    if (triageState.category) params.append('category', triageState.category);
    if (triageState.priority) params.append('priority', triageState.priority);
    if (triageState.hostelBlock) params.append('hostelBlock', triageState.hostelBlock);
    if (triageState.fromDate) params.append('fromDate', triageState.fromDate);
    if (triageState.toDate) params.append('toDate', triageState.toDate);
    if (triageState.q) params.append('q', triageState.q);

    try {
        const data = await apiRequest(`/admin/triage?${params.toString()}`);

        // Update live counters
        if (data.counters) {
            const elUnassigned = document.getElementById('triageCountUnassigned');
            const elOverdue = document.getElementById('triageCountOverdue');
            const elReopened = document.getElementById('triageCountReopened');
            const elHigh = document.getElementById('triageCountHighPriority');

            if (elUnassigned) elUnassigned.textContent = data.counters.unassigned ?? 0;
            if (elOverdue) elOverdue.textContent = data.counters.overdue ?? 0;
            if (elReopened) elReopened.textContent = data.counters.reopened ?? 0;
            if (elHigh) elHigh.textContent = data.counters.highPriority ?? 0;
        }

        const complaints = data.complaints || [];
        const total = data.total || 0;

        renderComplaintsTable(complaints, wrapper, () => loadTriageBoard());
        renderPagination(total, triageState, 'triagePagination', () => loadTriageBoard());
    } catch (err) {
        wrapper.innerHTML = `
            <div class="inline-error-state" style="padding: 24px; text-align: center; border: 1px solid var(--border-color); background: #fff; margin: 16px 0;">
                <div style="color: var(--crimson); margin-bottom: 8px;">${getIcon('alert-triangle', '', 28)}</div>
                <p style="margin-bottom: 12px; font-weight: 600;">Failed to load triage queue: ${escapeHtml(err.message)}</p>
                <button class="btn btn-outline btn-sm triage-retry-btn" type="button">${getIcon('refresh-cw', '', 14)} Retry</button>
            </div>
        `;
        wrapper.querySelector('.triage-retry-btn')?.addEventListener('click', () => loadTriageBoard());
    }
}

export async function loadAdminComplaints() {
    const wrapper = document.getElementById('adminComplaints');
    if (!wrapper) return;

    wrapper.innerHTML = `
        <div class="skeleton-table" aria-busy="true" aria-label="Loading content">
            <div class="skeleton-row" style="height:44px; margin-bottom:8px; border-radius:4px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
            <div class="skeleton-row" style="height:40px; margin-bottom:6px; border-radius:4px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
            <div class="skeleton-row" style="height:40px; margin-bottom:6px; border-radius:4px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
            <div class="skeleton-row" style="height:40px; margin-bottom:6px; border-radius:4px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
        </div>
    `;

    const params = new URLSearchParams({
        page: allState.page,
        size: allState.size
    });
    if (allState.status) params.append('status', allState.status);
    if (allState.category) params.append('category', allState.category);
    if (allState.priority) params.append('priority', allState.priority);
    if (allState.hostelBlock) params.append('hostelBlock', allState.hostelBlock);
    if (allState.fromDate) params.append('fromDate', allState.fromDate);
    if (allState.toDate) params.append('toDate', allState.toDate);
    if (allState.q) params.append('q', allState.q);

    try {
        const data = await apiRequest(`/admin/complaints?${params.toString()}`);
        const complaints = data.complaints || [];
        const total = data.total || 0;

        renderComplaintsTable(complaints, wrapper, () => loadAdminComplaints());
        renderPagination(total, allState, 'adminPagination', () => loadAdminComplaints());
    } catch (err) {
        wrapper.innerHTML = `
            <div class="inline-error-state" style="padding: 24px; text-align: center; border: 1px solid var(--border-color); background: #fff; margin: 16px 0;">
                <div style="color: var(--crimson); margin-bottom: 8px;">${getIcon('alert-triangle', '', 28)}</div>
                <p style="margin-bottom: 12px; font-weight: 600;">Failed to load complaints: ${escapeHtml(err.message)}</p>
                <button class="btn btn-outline btn-sm all-retry-btn" type="button">${getIcon('refresh-cw', '', 14)} Retry</button>
            </div>
        `;
        wrapper.querySelector('.all-retry-btn')?.addEventListener('click', () => loadAdminComplaints());
    }
}

export async function openAssignModal(complaintId, category, onRefresh) {
    window.openAssignModal = openAssignModal;
    const modal = document.getElementById('assignModal');
    const staffListEl = document.getElementById('assignStaffList');
    const autoBtn = document.getElementById('autoAssignBtn');
    const closeBtn = document.getElementById('closeAssignModal');

    if (!modal || !staffListEl) return;

    modal.style.display = 'flex';
    staffListEl.innerHTML = '<div class="loading">Fetching staff members...</div>';

    const triggerRefresh = () => {
        if (typeof onRefresh === 'function') {
            onRefresh();
        } else if (currentSubView === 'triage') {
            loadTriageBoard();
        } else {
            loadAdminComplaints();
        }
    };

    closeBtn.onclick = () => { modal.style.display = 'none'; };
    modal.querySelector('.modal-overlay').onclick = () => { modal.style.display = 'none'; };

    if (autoBtn) {
        autoBtn.onclick = async () => {
            try {
                await apiRequest(`/admin/complaints/${complaintId}/auto-assign`, { method: 'POST' });
                showToast('Auto-assigned successfully!', 'success');
                modal.style.display = 'none';
                triggerRefresh();
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
                    triggerRefresh();
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
