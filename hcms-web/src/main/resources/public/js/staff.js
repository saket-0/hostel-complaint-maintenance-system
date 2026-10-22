/**
 * Maintenance Staff Dashboard Module (P4 Overhaul).
 * 
 * Features:
 * - Prioritized location-first task cards ("Block B · Room 410")
 * - Clear reporter labeling ("Reported by Karthik Reddy")
 * - Overdue & priority sorting with 3 grouped queues: Reopened, Needs Action Now, In Progress
 * - Student reopen reason alert box directly rendered on reopened cards
 * - Unified "History & Completed" view with status filter & relative/absolute timestamps
 * - Distinct hover, focus, and selected states for all task cards
 * - Informative availability toggle with automatic assignment pause tooltip
 */
import { apiRequest } from './api.js';
import { showToast, escapeHtml } from './toast.js';
import { openComplaintModal } from './student.js';
import { formatExactDate, formatRelativeDate, formatDueStatus } from './dates.js';
import { getIcon } from './icons.js';
import { getPriorityInfo } from './app.js';

// Module state
let currentModeIsCompleted = false;
let activeQueueFilter = 'all';
let activeSort = 'overdue-priority';
let activeSearchQuery = '';
let historyStatusFilter = 'ALL';
let historySearchQuery = '';

let allActiveTasks = [];
let allHistoryTasks = [];
let controlsInitialized = false;

const CATEGORY_ICONS = {
    ELECTRICAL: 'zap',
    PLUMBING: 'droplet',
    INTERNET: 'wifi',
    NETWORK: 'wifi',
    CARPENTRY: 'wrench',
    CLEANLINESS: 'building',
    HOUSEKEEPING: 'building',
    APPLIANCE: 'zap',
    FURNITURE: 'wrench',
    OTHER: 'clipboard'
};

/**
 * Initializes the Staff Console view.
 * @param {boolean} isCompleted Whether to open in History & Completed mode
 */
export function initStaffView(isCompleted = false) {
    currentModeIsCompleted = isCompleted;

    const titleEl = document.getElementById('staffDashboardTitle');
    const availContainer = document.getElementById('staffAvailabilityContainer');
    const activePanel = document.getElementById('staffActivePanel');
    const historyPanel = document.getElementById('staffHistoryPanel');
    const tabActive = document.getElementById('tabStaffActive');
    const tabCompleted = document.getElementById('tabStaffCompleted');

    if (titleEl) {
        titleEl.textContent = isCompleted ? 'Task History & Completed' : 'Maintenance Console';
    }

    if (availContainer) {
        availContainer.style.display = isCompleted ? 'none' : 'flex';
    }

    if (activePanel && historyPanel) {
        activePanel.style.display = isCompleted ? 'none' : 'block';
        historyPanel.style.display = isCompleted ? 'block' : 'none';
    }

    if (tabActive && tabCompleted) {
        tabActive.classList.toggle('active', !isCompleted);
        tabCompleted.classList.toggle('active', isCompleted);
    }

    setupStaffControls();

    if (isCompleted) {
        loadStaffHistory();
    } else {
        loadStaffTasks();
    }
}

/**
 * Binds DOM event listeners for search, sort, tabs, and filters.
 */
function setupStaffControls() {
    if (controlsInitialized) return;
    controlsInitialized = true;

    // Availability toggle
    const toggle = document.getElementById('availabilityToggle');
    if (toggle) {
        toggle.onchange = async () => {
            const available = toggle.checked;
            try {
                await apiRequest('/staff/availability', {
                    method: 'PUT',
                    body: { available }
                });
                showToast(`Availability set to: ${available ? 'Available (receiving tasks)' : 'Busy (auto-assignment paused)'}`, 'info');
            } catch (err) {
                toggle.checked = !available;
                showToast(err.message, 'error');
            }
        };
    }

    // Sub-tabs: Active vs History
    const tabActive = document.getElementById('tabStaffActive');
    const tabCompleted = document.getElementById('tabStaffCompleted');

    tabActive?.addEventListener('click', () => {
        if (currentModeIsCompleted) {
            initStaffView(false);
            window.location.hash = '#/tasks';
        }
    });

    tabCompleted?.addEventListener('click', () => {
        if (!currentModeIsCompleted) {
            initStaffView(true);
            window.location.hash = '#/completed';
        }
    });

    // Active Search input
    const searchInput = document.getElementById('staffSearchInput');
    let searchDebounce = null;
    searchInput?.addEventListener('input', () => {
        clearTimeout(searchDebounce);
        searchDebounce = setTimeout(() => {
            activeSearchQuery = (searchInput.value || '').trim().toLowerCase();
            renderActiveQueues();
        }, 150);
    });

    // Active Sort select
    const sortSelect = document.getElementById('staffSortSelect');
    sortSelect?.addEventListener('change', () => {
        activeSort = sortSelect.value || 'overdue-priority';
        renderActiveQueues();
    });

    // KPI Queue buttons
    const kpiStrip = document.getElementById('staffKpiStrip');
    kpiStrip?.querySelectorAll('.staff-kpi-card').forEach(btn => {
        btn.addEventListener('click', () => {
            kpiStrip.querySelectorAll('.staff-kpi-card').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            activeQueueFilter = btn.getAttribute('data-queue') || 'all';
            renderActiveQueues();
        });
    });

    // History Search input
    const historySearch = document.getElementById('staffHistorySearchInput');
    let historyDebounce = null;
    historySearch?.addEventListener('input', () => {
        clearTimeout(historyDebounce);
        historyDebounce = setTimeout(() => {
            historySearchQuery = (historySearch.value || '').trim().toLowerCase();
            renderHistoryTasks();
        }, 150);
    });

    // History Status filter select
    const historyStatus = document.getElementById('staffHistoryStatusFilter');
    historyStatus?.addEventListener('change', () => {
        historyStatusFilter = historyStatus.value || 'ALL';
        renderHistoryTasks();
    });
}

/**
 * Loads active tasks assigned to the maintenance staff member.
 */
export async function loadStaffTasks() {
    const listEl = document.getElementById('staffTasks');
    if (!listEl) return;

    listEl.innerHTML = `
        <div class="skeleton-card" style="height:110px; border-radius:4px; margin-bottom:12px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
        <div class="skeleton-card" style="height:110px; border-radius:4px; margin-bottom:12px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
    `;

    try {
        const data = await apiRequest('/staff/tasks');
        const tasks = (data && data.tasks) || [];

        // Defensively enrich tasks with student location and reopen reasons if needed
        await Promise.allSettled(tasks.map(async (t) => {
            try {
                if (!t.studentHostelBlock || t.status === 'REOPENED') {
                    const detail = await apiRequest(`/complaints/${t.complaintId}`);
                    if (detail) {
                        t.studentHostelBlock = detail.studentHostelBlock || t.studentHostelBlock;
                        t.studentRoomNumber = detail.studentRoomNumber || t.studentRoomNumber;
                        t.studentPhone = detail.studentPhone;
                        if (detail.history && Array.isArray(detail.history)) {
                            const reopenEntry = [...detail.history].reverse().find(h => h.newStatus === 'REOPENED');
                            if (reopenEntry && reopenEntry.note) {
                                t.reopenReason = reopenEntry.note;
                            }
                        }
                    }
                }
            } catch (err) {
                // Silently fallback to basic task fields
            }
        }));

        allActiveTasks = tasks;
        updateActiveKpiCounters(tasks);
        renderActiveQueues();

    } catch (err) {
        listEl.innerHTML = `
            <div class="inline-error-state" style="padding: 24px; text-align: center; border: 1px solid var(--border-color); background: #fff; margin: 16px 0;">
                <div style="color: var(--crimson); margin-bottom: 8px;">${getIcon('alert-triangle', '', 28)}</div>
                <p style="margin-bottom: 12px; font-weight: 600;">Failed to load active tasks: ${escapeHtml(err.message)}</p>
                <button class="btn btn-outline btn-sm staff-retry-btn" type="button">${getIcon('refresh-cw', '', 14)} Retry</button>
            </div>
        `;
        listEl.querySelector('.staff-retry-btn')?.addEventListener('click', () => loadStaffTasks());
    }
}

/**
 * Updates KPI summary numbers in the top strip.
 */
function updateActiveKpiCounters(tasks) {
    const totalCount = tasks.length;
    const reopenedCount = tasks.filter(t => t.status === 'REOPENED').length;
    const assignedCount = tasks.filter(t => t.status === 'ASSIGNED').length;
    const inProgressCount = tasks.filter(t => t.status === 'IN_PROGRESS').length;
    const overdueCount = tasks.filter(t => t.overdue).length;

    const elTotal = document.getElementById('kpiValAll');
    const elReopened = document.getElementById('kpiValReopened');
    const elAssigned = document.getElementById('kpiValAssigned');
    const elInProgress = document.getElementById('kpiValInProgress');
    const elOverdue = document.getElementById('kpiValOverdue');
    const tabActiveCount = document.getElementById('countStaffActive');

    if (elTotal) elTotal.textContent = totalCount;
    if (elReopened) elReopened.textContent = reopenedCount;
    if (elAssigned) elAssigned.textContent = assignedCount;
    if (elInProgress) elInProgress.textContent = inProgressCount;
    if (elOverdue) elOverdue.textContent = overdueCount;
    if (tabActiveCount) tabActiveCount.textContent = totalCount;
}

/**
 * Sorts tasks according to specified criteria.
 */
function sortTaskList(tasks, sortKey) {
    const list = [...tasks];
    list.sort((a, b) => {
        if (sortKey === 'overdue-priority') {
            if (a.overdue !== b.overdue) return a.overdue ? -1 : 1;
            const pA = getPriorityInfo(a.priority).level;
            const pB = getPriorityInfo(b.priority).level;
            if (pA !== pB) return pA - pB;
            const dueA = a.dueBy ? new Date(a.dueBy).getTime() : Infinity;
            const dueB = b.dueBy ? new Date(b.dueBy).getTime() : Infinity;
            return dueA - dueB;
        } else if (sortKey === 'due-soonest') {
            const dueA = a.dueBy ? new Date(a.dueBy).getTime() : Infinity;
            const dueB = b.dueBy ? new Date(b.dueBy).getTime() : Infinity;
            return dueA - dueB;
        } else if (sortKey === 'newest') {
            const cA = a.createdDate ? new Date(a.createdDate).getTime() : 0;
            const cB = b.createdDate ? new Date(b.createdDate).getTime() : 0;
            return cB - cA;
        } else if (sortKey === 'oldest') {
            const cA = a.createdDate ? new Date(a.createdDate).getTime() : 0;
            const cB = b.createdDate ? new Date(b.createdDate).getTime() : 0;
            return cA - cB;
        }
        return 0;
    });
    return list;
}

/**
 * Filters and renders the 3 visual queues: Reopened, Needs Action Now, In Progress.
 */
function renderActiveQueues() {
    const container = document.getElementById('staffTasks');
    if (!container) return;

    let filtered = [...allActiveTasks];

    // Filter by search query
    if (activeSearchQuery) {
        filtered = filtered.filter(t => {
            const block = (t.studentHostelBlock || t.hostelBlock || '').toLowerCase();
            const room = (t.studentRoomNumber || t.roomNumber || '').toLowerCase();
            const student = (t.studentName || '').toLowerCase();
            const desc = (t.description || '').toLowerCase();
            const id = (t.complaintId || '').toLowerCase();
            const cat = (t.categoryDisplay || t.category || '').toLowerCase();
            return block.includes(activeSearchQuery) ||
                room.includes(activeSearchQuery) ||
                student.includes(activeSearchQuery) ||
                desc.includes(activeSearchQuery) ||
                id.includes(activeSearchQuery) ||
                cat.includes(activeSearchQuery);
        });
    }

    // Filter by KPI strip queue
    if (activeQueueFilter === 'reopened') {
        filtered = filtered.filter(t => t.status === 'REOPENED');
    } else if (activeQueueFilter === 'assigned') {
        filtered = filtered.filter(t => t.status === 'ASSIGNED');
    } else if (activeQueueFilter === 'inprogress') {
        filtered = filtered.filter(t => t.status === 'IN_PROGRESS');
    } else if (activeQueueFilter === 'overdue') {
        filtered = filtered.filter(t => t.overdue);
    }

    // Apply sorting
    filtered = sortTaskList(filtered, activeSort);

    if (filtered.length === 0) {
        container.innerHTML = `
            <div class="empty-state" style="padding: 40px 20px; text-align: center;">
                <div class="empty-icon" style="margin-bottom: 12px; color: var(--grey);">${getIcon('inbox', '', 40)}</div>
                <h3 style="margin-bottom: 6px;">All caught up!</h3>
                <p style="color: var(--grey);">${activeSearchQuery || activeQueueFilter !== 'all' ? 'No tasks matched your active filter or search.' : 'No pending maintenance tasks assigned to you right now.'}</p>
            </div>
        `;
        return;
    }

    // Split into 3 Queues
    const reopenedList = filtered.filter(t => t.status === 'REOPENED');
    const assignedList = filtered.filter(t => t.status === 'ASSIGNED');
    const inProgressList = filtered.filter(t => t.status === 'IN_PROGRESS');
    // Any remaining non-matching status fallback
    const otherList = filtered.filter(t => t.status !== 'REOPENED' && t.status !== 'ASSIGNED' && t.status !== 'IN_PROGRESS');

    let html = '';

    // Queue 1: Reopened (Priority Alert Queue)
    if (reopenedList.length > 0 || activeQueueFilter === 'reopened') {
        html += `
            <div class="task-queue-group queue-reopened" id="queueReopenedGroup">
                <div class="task-queue-header">
                    <div class="task-queue-title-wrap">
                        <span class="text-red">${getIcon('alert-triangle', '', 20)}</span>
                        <h3 class="text-red">Reopened Tasks</h3>
                        <span class="task-queue-badge badge-reopened">${reopenedList.length}</span>
                    </div>
                    <p class="task-queue-desc">Student reported issue still persists. Inspect and resolve with high priority.</p>
                </div>
                <div class="task-queue-list">
                    ${reopenedList.length > 0 
                        ? reopenedList.map(t => renderTaskCardHtml(t)).join('')
                        : '<div class="task-queue-empty">No reopened tasks requiring re-inspection.</div>'}
                </div>
            </div>
        `;
    }

    // Queue 2: Needs Action Now (Assigned Queue)
    if (assignedList.length > 0 || activeQueueFilter === 'assigned') {
        html += `
            <div class="task-queue-group queue-assigned" id="queueAssignedGroup">
                <div class="task-queue-header">
                    <div class="task-queue-title-wrap">
                        <span class="text-navy">${getIcon('wrench', '', 20)}</span>
                        <h3>Needs Action Now</h3>
                        <span class="task-queue-badge badge-assigned">${assignedList.length}</span>
                    </div>
                    <p class="task-queue-desc">New maintenance tasks assigned to you. Click Accept to begin work.</p>
                </div>
                <div class="task-queue-list">
                    ${assignedList.length > 0
                        ? assignedList.map(t => renderTaskCardHtml(t)).join('')
                        : '<div class="task-queue-empty">No new assigned tasks waiting to be accepted.</div>'}
                </div>
            </div>
        `;
    }

    // Queue 3: In Progress Queue
    if (inProgressList.length > 0 || activeQueueFilter === 'inprogress') {
        html += `
            <div class="task-queue-group queue-inprogress" id="queueInProgressGroup">
                <div class="task-queue-header">
                    <div class="task-queue-title-wrap">
                        <span class="text-teal">${getIcon('refresh-cw', '', 20)}</span>
                        <h3>In Progress</h3>
                        <span class="task-queue-badge badge-inprogress">${inProgressList.length}</span>
                    </div>
                    <p class="task-queue-desc">Repairs currently underway. Mark as Resolved once work is completed.</p>
                </div>
                <div class="task-queue-list">
                    ${inProgressList.length > 0
                        ? inProgressList.map(t => renderTaskCardHtml(t)).join('')
                        : '<div class="task-queue-empty">No active tasks in progress.</div>'}
                </div>
            </div>
        `;
    }

    // Other statuses fallback if any
    if (otherList.length > 0) {
        html += `
            <div class="task-queue-group" id="queueOtherGroup">
                <div class="task-queue-header">
                    <div class="task-queue-title-wrap">
                        <span class="text-navy">${getIcon('clipboard', '', 20)}</span>
                        <h3>Other Active Tasks</h3>
                        <span class="task-queue-badge">${otherList.length}</span>
                    </div>
                </div>
                <div class="task-queue-list">
                    ${otherList.map(t => renderTaskCardHtml(t)).join('')}
                </div>
            </div>
        `;
    }

    container.innerHTML = html;
    bindCardEvents(container, false);
}

/**
 * Standardizes timestamp display: relative under 7 days with exact tooltip, absolute after 7 days.
 */
function formatTaskTimestamp(dateStr, label = 'Resolved') {
    if (!dateStr) return '—';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    const diffMs = Date.now() - d.getTime();
    const exact = formatExactDate(dateStr);
    if (diffMs >= 0 && diffMs < 7 * 86400000) {
        return `<span title="${escapeHtml(exact)}" style="cursor:help;">${label} ${formatRelativeDate(dateStr)}</span>`;
    } else {
        return `<span title="${label} on ${escapeHtml(exact)}">${label} ${escapeHtml(exact)}</span>`;
    }
}

/**
 * Formats prominent location text from student hostel block and room number.
 */
function formatLocationDisplay(task) {
    const rawBlock = task.studentHostelBlock || task.hostelBlock || '';
    const rawRoom = task.studentRoomNumber || task.roomNumber || '';

    if (!rawBlock && !rawRoom) {
        return 'Campus · Common Area';
    }

    let blockText = '';
    if (rawBlock) {
        blockText = rawBlock.toLowerCase().startsWith('block') ? rawBlock : `Block ${rawBlock}`;
    } else {
        blockText = 'Hostel';
    }

    const roomText = rawRoom ? ` · Room ${rawRoom}` : '';
    return `${blockText}${roomText}`;
}

/**
 * Renders HTML string for a single staff task card.
 */
function renderTaskCardHtml(task, isHistory = false) {
    const pInfo = getPriorityInfo(task.priority);
    const pLabel = pInfo.label;
    const pClass = pInfo.class;
    const isOverdue = task.overdue;
    const locationText = formatLocationDisplay(task);

    const allowed = task.allowedActions || [];
    let actionHtml = '';
    if (allowed.includes('ACCEPT') || task.status === 'ASSIGNED' || task.status === 'REOPENED') {
        actionHtml = `<button class="btn btn-primary btn-sm btn-accept" data-id="${task.complaintId}" type="button">Accept Task</button>`;
    } else if (allowed.includes('RESOLVE') || task.status === 'IN_PROGRESS') {
        actionHtml = `<button class="btn btn-teal btn-sm btn-resolve" data-id="${task.complaintId}" type="button">Mark as Resolved</button>`;
    }

    // Feedback Rating review box
    let feedbackHtml = '';
    if (task.feedbackRating) {
        feedbackHtml = `
            <div class="task-review" style="margin-top: 8px; padding: 8px 12px; background: #FFFBEB; border: 1px solid #FDE68A; border-radius: 4px; font-size: 0.85rem;">
                <span style="color: #B45309; font-weight: 700;">★ ${task.feedbackRating}/5 Student Rating:</span>
                ${task.feedbackComment ? `<em style="color:#78350F; margin-left:4px;">"${escapeHtml(task.feedbackComment)}"</em>` : ''}
            </div>
        `;
    }

    // Date / SLA display
    const dateDisplay = isHistory
        ? formatTaskTimestamp(task.resolvedDate || task.updatedDate, 'Resolved')
        : `<span title="${formatExactDate(task.dueBy)}">${formatDueStatus(task.dueBy, task.status)}</span>`;

    // Category icon
    const catName = (task.category || '').toUpperCase();
    const catIcon = CATEGORY_ICONS[catName] || 'clipboard';

    return `
        <div class="complaint-card staff-task-card ${isOverdue ? 'is-overdue' : ''} ${task.status === 'REOPENED' ? 'is-reopened' : ''}" data-id="${task.complaintId}" tabindex="0" role="article" aria-label="Task ${task.complaintId}">
            <!-- Top Header: Prominent Location & Status Badges -->
            <div class="task-card-header">
                <div class="task-location-primary">
                    <span class="task-location-pin">${getIcon('building', '', 16)}</span>
                    <strong class="task-location-text">${escapeHtml(locationText)}</strong>
                </div>
                <div class="task-meta-badges">
                    <span class="priority-badge ${pClass}">${escapeHtml(pLabel)}</span>
                    <span class="status-badge status-${task.status}">${escapeHtml(task.statusDisplay || task.status)}</span>
                    ${isOverdue ? '<span class="badge badge-overdue font-bold" style="background:#DC2626; color:#fff; padding:2px 6px; border-radius:3px; font-size:0.7rem;">OVERDUE</span>' : ''}
                </div>
            </div>

            <!-- Reporter Row -->
            <div class="task-reporter-row">
                <span class="task-reporter">
                    ${getIcon('user', '', 14)} Reported by <strong>${escapeHtml(task.studentName || 'Student')}</strong>
                    ${task.studentPhone ? `<span class="task-reporter-phone">· 📞 ${escapeHtml(task.studentPhone)}</span>` : ''}
                </span>
                <span class="complaint-id font-mono">#${escapeHtml(task.complaintId)}</span>
            </div>

            <!-- Student Reopen Reason Alert Box -->
            ${task.status === 'REOPENED' ? `
            <div class="task-reopen-alert">
                <div class="task-reopen-header">
                    ${getIcon('alert-triangle', '', 15)} <strong>Student Reopen Reason:</strong>
                </div>
                <p class="task-reopen-reason">"${escapeHtml(task.reopenReason || 'Student indicated problem was not resolved and requested re-inspection.')}"</p>
            </div>
            ` : ''}

            <!-- Card Body: Category & Description -->
            <div class="task-card-body">
                <div class="task-category-pill">
                    ${getIcon(catIcon, '', 13)} ${escapeHtml(task.categoryDisplay || task.category)}
                </div>
                <p class="task-description">${escapeHtml(task.description)}</p>
                ${feedbackHtml}
            </div>

            <!-- Footer: Timestamp & Actions -->
            <div class="task-card-footer">
                <div class="task-sla-time">
                    <span class="task-time-icon">${getIcon('clock', '', 13)}</span>
                    ${dateDisplay}
                </div>
                <div class="task-card-actions">
                    <button class="btn btn-outline btn-sm btn-view-details" data-id="${task.complaintId}" type="button">Details</button>
                    ${actionHtml}
                </div>
            </div>
        </div>
    `;
}

/**
 * Loads unified completed and task history records.
 */
export async function loadStaffHistory() {
    const listEl = document.getElementById('staffHistoryTasks');
    if (!listEl) return;

    listEl.innerHTML = `
        <div class="skeleton-card" style="height:110px; border-radius:4px; margin-bottom:12px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
        <div class="skeleton-card" style="height:110px; border-radius:4px; margin-bottom:12px; background:linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%); background-size:200% 100%; animation:shimmer 1.5s infinite;"></div>
    `;

    try {
        const data = await apiRequest('/staff/tasks/history');
        const tasks = (data && data.tasks) || [];

        allHistoryTasks = tasks;
        const tabCompletedCount = document.getElementById('countStaffCompleted');
        if (tabCompletedCount) tabCompletedCount.textContent = tasks.length;

        renderHistoryTasks();

    } catch (err) {
        listEl.innerHTML = `
            <div class="inline-error-state" style="padding: 24px; text-align: center; border: 1px solid var(--border-color); background: #fff; margin: 16px 0;">
                <div style="color: var(--crimson); margin-bottom: 8px;">${getIcon('alert-triangle', '', 28)}</div>
                <p style="margin-bottom: 12px; font-weight: 600;">Failed to load task history: ${escapeHtml(err.message)}</p>
                <button class="btn btn-outline btn-sm staff-history-retry-btn" type="button">${getIcon('refresh-cw', '', 14)} Retry</button>
            </div>
        `;
        listEl.querySelector('.staff-history-retry-btn')?.addEventListener('click', () => loadStaffHistory());
    }
}

/**
 * Renders filtered history cards with search and status dropdown filtering.
 */
function renderHistoryTasks() {
    const container = document.getElementById('staffHistoryTasks');
    if (!container) return;

    let filtered = [...allHistoryTasks];

    // Status filter
    if (historyStatusFilter !== 'ALL') {
        filtered = filtered.filter(t => t.status === historyStatusFilter);
    }

    // Search query filter
    if (historySearchQuery) {
        filtered = filtered.filter(t => {
            const block = (t.studentHostelBlock || t.hostelBlock || '').toLowerCase();
            const room = (t.studentRoomNumber || t.roomNumber || '').toLowerCase();
            const student = (t.studentName || '').toLowerCase();
            const desc = (t.description || '').toLowerCase();
            const id = (t.complaintId || '').toLowerCase();
            return block.includes(historySearchQuery) ||
                room.includes(historySearchQuery) ||
                student.includes(historySearchQuery) ||
                desc.includes(historySearchQuery) ||
                id.includes(historySearchQuery);
        });
    }

    if (filtered.length === 0) {
        container.innerHTML = `
            <div class="empty-state" style="padding: 40px 20px; text-align: center;">
                <div class="empty-icon" style="margin-bottom: 12px; color: var(--grey);">${getIcon('inbox', '', 40)}</div>
                <h3 style="margin-bottom: 6px;">No completed tasks found</h3>
                <p style="color: var(--grey);">${historySearchQuery || historyStatusFilter !== 'ALL' ? 'No records match your selected filter or search.' : 'Tasks you mark as resolved will appear here.'}</p>
            </div>
        `;
        return;
    }

    container.innerHTML = filtered.map(t => renderTaskCardHtml(t, true)).join('');
    bindCardEvents(container, true);
}

/**
 * Binds card click, keyboard navigation, and action buttons.
 */
function bindCardEvents(container, isHistory = false) {
    // Card select & modal open
    container.querySelectorAll('.staff-task-card').forEach(card => {
        const id = card.getAttribute('data-id');

        card.addEventListener('click', (e) => {
            if (e.target.closest('.btn-accept') || e.target.closest('.btn-resolve')) return;
            container.querySelectorAll('.staff-task-card').forEach(c => c.classList.remove('selected'));
            card.classList.add('selected');
            openComplaintModal(id);
        });

        card.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                if (e.target.closest('.btn-accept') || e.target.closest('.btn-resolve')) return;
                e.preventDefault();
                container.querySelectorAll('.staff-task-card').forEach(c => c.classList.remove('selected'));
                card.classList.add('selected');
                openComplaintModal(id);
            }
        });
    });

    // View Details button
    container.querySelectorAll('.btn-view-details').forEach(btn => {
        btn.onclick = (e) => {
            e.stopPropagation();
            openComplaintModal(btn.getAttribute('data-id'));
        };
    });

    // Accept task button
    container.querySelectorAll('.btn-accept').forEach(btn => {
        btn.onclick = async (e) => {
            e.stopPropagation();
            const id = btn.getAttribute('data-id');
            btn.disabled = true;
            try {
                await apiRequest(`/staff/tasks/${id}/accept`, { method: 'POST' });
                showToast(`Task #${id} accepted. Status updated to IN PROGRESS.`, 'success');
                loadStaffTasks();
            } catch (err) {
                btn.disabled = false;
                showToast(err.message, 'error');
            }
        };
    });

    // Mark as Resolved button
    container.querySelectorAll('.btn-resolve').forEach(btn => {
        btn.onclick = async (e) => {
            e.stopPropagation();
            const id = btn.getAttribute('data-id');
            const note = prompt('Please enter work done / resolution notes:');
            if (note === null) return;
            btn.disabled = true;
            try {
                await apiRequest(`/staff/tasks/${id}/status`, {
                    method: 'PUT',
                    body: {
                        status: 'RESOLVED',
                        note: note || 'Resolved by maintenance staff'
                    }
                });
                showToast(`Task #${id} marked as RESOLVED!`, 'success');
                loadStaffTasks();
            } catch (err) {
                btn.disabled = false;
                showToast(err.message, 'error');
            }
        };
    });
}
