/**
 * Admin Dashboard Module.
 * Manages triage board, filtering, pagination, priority override,
 * staff assignment (manual and auto-assign strategy), CSV export,
 * analytics with charts, and staff management.
 */
import { apiRequest } from './api.js';
import { showToast, escapeHtml } from './toast.js';
import { openComplaintModal, closeComplaintDrawer, formatDate } from './student.js';
import { formatExactDate, formatRelativeDate, formatDueStatus } from './dates.js';
import { getIcon } from './icons.js';
import { getCurrentUser } from './auth.js';
import { getPriorityInfo, formatStatus } from './app.js';

let currentSubView = 'triage';

const triageState = {
    page: 0,
    size: 25,
    sort: 'default',
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
    sort: 'default',
    counter: '',
    q: '',
    status: '',
    category: '',
    priority: '',
    hostelBlock: '',
    fromDate: '',
    toDate: ''
};

const selectedComplaintIds = new Set();

let statusChart = null;
let categoryChart = null;
let priorityChart = null;
let trendChart = null;

function getCategoryIcon(cat) {
    switch ((cat || '').toUpperCase()) {
        case 'ELECTRICAL': return getIcon('zap', 'cat-icon', 14);
        case 'PLUMBING': return getIcon('droplet', 'cat-icon', 14);
        case 'WIFI': return getIcon('wifi', 'cat-icon', 14);
        case 'CARPENTRY':
        case 'AC':
        case 'CLEANING':
        default: return getIcon('wrench', 'cat-icon', 14);
    }
}

function detectDuplicates(complaints) {
    const dupMap = new Map();
    complaints.forEach(c => {
        const block = (c.hostelBlock || c.studentHostelBlock || '').trim().toUpperCase();
        const room = (c.roomNumber || c.studentRoomNumber || '').trim().toUpperCase();
        const cat = (c.category || '').trim().toUpperCase();
        if (block && room && cat) {
            const key = `${cat}::${block}::${room}`;
            if (!dupMap.has(key)) dupMap.set(key, []);
            dupMap.get(key).push(c.complaintId);
        }
    });
    return dupMap;
}

function sortComplaints(complaints, sortMode = 'default') {
    const isOverdue = (c) => Boolean(c.overdue || (c.dueBy && new Date(c.dueBy) < new Date() && c.status !== 'RESOLVED' && c.status !== 'CLOSED'));
    const getPriVal = (p) => {
        const pInfo = getPriorityInfo(p);
        return pInfo.key === 'HIGH' ? 1 : (pInfo.key === 'MEDIUM' ? 2 : 3);
    };

    const list = [...complaints];
    return list.sort((a, b) => {
        switch (sortMode) {
            case 'due_asc': {
                const ta = a.dueBy ? new Date(a.dueBy).getTime() : Infinity;
                const tb = b.dueBy ? new Date(b.dueBy).getTime() : Infinity;
                return ta - tb;
            }
            case 'due_desc': {
                const ta = a.dueBy ? new Date(a.dueBy).getTime() : -Infinity;
                const tb = b.dueBy ? new Date(b.dueBy).getTime() : -Infinity;
                return tb - ta;
            }
            case 'priority_desc': {
                return getPriVal(a.priority) - getPriVal(b.priority);
            }
            case 'priority_asc': {
                return getPriVal(b.priority) - getPriVal(a.priority);
            }
            case 'created_asc': {
                const ta = a.createdDate ? new Date(a.createdDate).getTime() : 0;
                const tb = b.createdDate ? new Date(b.createdDate).getTime() : 0;
                return ta - tb;
            }
            case 'created_desc': {
                const ta = a.createdDate ? new Date(a.createdDate).getTime() : 0;
                const tb = b.createdDate ? new Date(b.createdDate).getTime() : 0;
                return tb - ta;
            }
            case 'default':
            default: {
                const oa = isOverdue(a) ? 0 : 1;
                const ob = isOverdue(b) ? 0 : 1;
                if (oa !== ob) return oa - ob;

                const ta = a.dueBy ? new Date(a.dueBy).getTime() : Infinity;
                const tb = b.dueBy ? new Date(b.dueBy).getTime() : Infinity;
                if (ta !== tb) return ta - tb;

                const pa = getPriVal(a.priority);
                const pb = getPriVal(b.priority);
                if (pa !== pb) return pa - pb;

                const ca = a.createdDate ? new Date(a.createdDate).getTime() : 0;
                const cb = b.createdDate ? new Date(b.createdDate).getTime() : 0;
                return cb - ca;
            }
        }
    });
}

export function initAdminView(subroute = 'triage') {
    currentSubView = subroute;
    setupTriageToolbar();
    setupAllComplaintsToolbar();
    setupCsvExport();
    setupUserManagement();
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
        loadUserManagementView();
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

    // Sort select for Triage
    const triageSortSelect = document.getElementById('triageSortSelect');
    if (triageSortSelect) {
        triageSortSelect.value = triageState.sort;
        triageSortSelect.onchange = () => {
            triageState.sort = triageSortSelect.value;
            loadTriageBoard();
        };
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
        const pLabel = getPriorityInfo(triageState.priority).label;
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
    // 4 Clickable Summary Counter Cards for All Complaints
    const allCards = document.querySelectorAll('#allCountersGrid .triage-counter-card');
    allCards.forEach(card => {
        card.onclick = () => {
            const counterType = card.getAttribute('data-counter');
            if (allState.counter === counterType) {
                allState.counter = '';
                allState.status = '';
                allState.fromDate = '';
                allCards.forEach(c => {
                    c.classList.remove('active');
                    c.setAttribute('aria-pressed', 'false');
                });
            } else {
                allState.counter = counterType;
                allCards.forEach(c => {
                    c.classList.remove('active');
                    c.setAttribute('aria-pressed', 'false');
                });
                card.classList.add('active');
                card.setAttribute('aria-pressed', 'true');

                if (counterType === 'overdue') {
                    allState.status = '';
                    allState.sort = 'due_asc';
                    const sortEl = document.getElementById('allSortSelect');
                    if (sortEl) sortEl.value = 'due_asc';
                } else if (counterType === 'unassigned') {
                    allState.status = 'NEW';
                } else if (counterType === 'in_progress') {
                    allState.status = 'IN_PROGRESS';
                } else if (counterType === 'resolved_today') {
                    allState.status = 'RESOLVED';
                    const todayStr = new Date().toISOString().split('T')[0];
                    allState.fromDate = todayStr;
                }
            }
            allState.page = 0;
            updateAllChips();
            loadAdminComplaints();
        };
    });

    const searchInput = document.getElementById('allSearchInput');
    if (searchInput) {
        searchInput.oninput = debounce(() => {
            allState.q = searchInput.value.trim();
            allState.page = 0;
            updateAllChips();
            loadAdminComplaints();
        }, 300);
    }

    // Sort select for All Complaints
    const allSortSelect = document.getElementById('allSortSelect');
    if (allSortSelect) {
        allSortSelect.value = allState.sort;
        allSortSelect.onchange = () => {
            allState.sort = allSortSelect.value;
            loadAdminComplaints();
        };
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

    if (allState.counter) {
        filterCount++;
        const label = allState.counter.replace('_', ' ').toUpperCase();
        chips.push(`<span class="filter-chip">Queue: ${escapeHtml(label)} <button class="chip-remove" data-clear="counter" aria-label="Clear queue filter">✕</button></span>`);
    }
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
        const pLabel = getPriorityInfo(allState.priority).label;
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
                if (key === 'counter') {
                    allState.counter = '';
                    allState.status = '';
                    allState.fromDate = '';
                    document.querySelectorAll('#allCountersGrid .triage-counter-card').forEach(c => {
                        c.classList.remove('active');
                        c.setAttribute('aria-pressed', 'false');
                    });
                } else if (key === 'q') {
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
                allState.counter = '';
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
                document.querySelectorAll('#allCountersGrid .triage-counter-card').forEach(c => {
                    c.classList.remove('active');
                    c.setAttribute('aria-pressed', 'false');
                });
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
    const dropdownBtn = document.getElementById('exportDropdownBtn');
    const dropdownMenu = document.getElementById('exportDropdownMenu');
    if (dropdownBtn && dropdownMenu) {
        dropdownBtn.onclick = (e) => {
            e.stopPropagation();
            const isOpen = dropdownMenu.style.display === 'flex';
            dropdownMenu.style.display = isOpen ? 'none' : 'flex';
            dropdownBtn.setAttribute('aria-expanded', !isOpen);
        };

        document.addEventListener('click', (e) => {
            if (dropdownMenu.style.display === 'flex' && !dropdownMenu.contains(e.target) && e.target !== dropdownBtn) {
                dropdownMenu.style.display = 'none';
                dropdownBtn.setAttribute('aria-expanded', 'false');
            }
        });
    }

    const exportBtn = document.getElementById('exportCsvBtn');
    if (exportBtn) {
        exportBtn.onclick = async () => {
            if (dropdownMenu) dropdownMenu.style.display = 'none';
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
            if (dropdownMenu) dropdownMenu.style.display = 'none';
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

/**
 * Ensures auto-assign confirmation modal styles are injected without adding new fonts or hex colors.
 */
function ensureAutoAssignStyles() {
    if (document.getElementById('auto-assign-confirm-styles')) return;
    const style = document.createElement('style');
    style.id = 'auto-assign-confirm-styles';
    style.textContent = `
        #autoAssignConfirmModal .modal-content {
            padding: 0 !important;
            overflow: hidden !important;
            display: flex;
            flex-direction: column;
            background: var(--white);
            border: 3px solid var(--border-color);
            box-shadow: 8px 8px 0 var(--border-color);
        }
        #autoAssignConfirmModal.modal-single .modal-content {
            max-width: 520px;
            width: 92%;
        }
        #autoAssignConfirmModal.modal-group .modal-content {
            max-width: 720px;
            width: 95%;
        }
        .auto-assign-header {
            padding: 16px 20px;
            border-bottom: 2px solid var(--border-color);
            display: flex;
            align-items: center;
            justify-content: space-between;
            background: var(--white);
            flex-shrink: 0;
        }
        .auto-assign-header h3 {
            margin: 0;
            font-size: 1.15rem;
            font-weight: 800;
            color: var(--navy);
        }
        .auto-assign-summary-strip {
            position: sticky;
            top: 0;
            z-index: 10;
            background: var(--bg-light);
            border-bottom: 2px solid var(--border-color);
            padding: 8px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            font-size: 0.8rem;
            color: var(--navy);
            gap: 10px;
            overflow-x: hidden;
            white-space: nowrap;
            flex-shrink: 0;
        }
        .auto-assign-summary-stat {
            display: inline-flex;
            align-items: center;
            gap: 4px;
        }
        .auto-assign-summary-overdue {
            color: var(--red);
            font-weight: 700;
        }
        .auto-assign-scroll-body {
            padding: 14px 18px;
            max-height: 50vh;
            overflow-y: auto;
            overflow-x: hidden;
            flex: 1 1 auto;
        }
        .auto-assign-warnings {
            background: #FFFBEB;
            border: 1.5px solid var(--amber);
            padding: 10px 14px;
            margin-bottom: 14px;
            font-size: 0.82rem;
            color: #7A4F01;
        }
        .auto-assign-warnings strong {
            display: block;
            margin-bottom: 4px;
        }
        .auto-assign-warning-link {
            color: #7A4F01;
            text-decoration: underline;
            cursor: pointer;
            font-weight: 700;
        }
        .auto-assign-staff-group {
            border: 2px solid var(--border-color);
            background: var(--white);
            margin-bottom: 12px;
        }
        .auto-assign-staff-header {
            background: var(--bg-light);
            border-bottom: 2px solid var(--border-color);
            padding: 8px 12px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            font-size: 0.82rem;
            flex-wrap: nowrap;
            overflow: hidden;
        }
        .auto-assign-staff-header strong {
            color: var(--navy);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .auto-assign-staff-change {
            font-size: 0.78rem;
            font-family: var(--font-mono);
            color: var(--grey);
            white-space: nowrap;
            margin-left: 8px;
        }
        .auto-assign-ticket-row {
            padding: 8px 12px;
            border-bottom: 1px solid var(--border-color);
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            background: var(--white);
            outline: none;
            transition: background 0.15s ease;
        }
        .auto-assign-ticket-row:last-child {
            border-bottom: none;
        }
        .auto-assign-ticket-row:focus-visible, .auto-assign-ticket-row.is-focused {
            background: #F0FDF4;
            box-shadow: inset 3px 0 0 var(--teal);
        }
        .auto-assign-ticket-row.is-excluded {
            background: var(--bg-muted);
            opacity: 0.65;
        }
        .auto-assign-ticket-row.is-excluded .auto-assign-row-desc,
        .auto-assign-ticket-row.is-excluded .auto-assign-ticket-id {
            text-decoration: line-through;
        }
        .auto-assign-ticket-row.has-conflict {
            background: #FEF2F2 !important;
            border-left: 3px solid var(--red) !important;
            opacity: 1 !important;
        }
        .auto-assign-row-main {
            flex: 1 1 auto;
            min-width: 0;
            overflow: hidden;
        }
        .auto-assign-row-top {
            display: flex;
            align-items: center;
            gap: 6px;
            margin-bottom: 2px;
            flex-wrap: nowrap;
        }
        .auto-assign-ticket-id {
            font-family: var(--font-mono);
            font-weight: 700;
            font-size: 0.82rem;
            color: var(--navy);
            white-space: nowrap;
        }
        .auto-assign-row-desc {
            font-size: 0.8rem;
            color: var(--navy);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            max-width: 100%;
        }
        .auto-assign-row-meta {
            font-size: 0.74rem;
            color: var(--grey);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .auto-assign-row-actions {
            display: flex;
            align-items: center;
            gap: 6px;
            flex-shrink: 0;
        }
        .auto-assign-remove-btn {
            background: transparent;
            border: 1px solid var(--border-color);
            border-radius: 2px;
            width: 22px;
            height: 22px;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            font-size: 0.75rem;
            font-weight: 800;
            color: var(--grey);
            padding: 0;
        }
        .auto-assign-remove-btn:hover {
            background: var(--red);
            color: var(--white);
            border-color: var(--red);
        }
        .auto-assign-undo-btn {
            background: var(--white);
            border: 1px solid var(--border-color);
            padding: 2px 6px;
            font-size: 0.72rem;
            cursor: pointer;
            font-weight: 700;
        }
        .auto-assign-undo-btn:hover {
            background: var(--teal);
            color: var(--white);
        }
        .auto-assign-change-select {
            padding: 2px 4px;
            font-size: 0.75rem;
            border: 1.5px solid var(--border-color);
            background: var(--white);
            max-width: 130px;
        }
        .auto-assign-conflict-msg {
            color: var(--red);
            font-weight: 700;
            font-size: 0.75rem;
            margin-top: 4px;
        }
        .auto-assign-footer {
            position: sticky;
            bottom: 0;
            z-index: 10;
            background: var(--white);
            border-top: 2px solid var(--border-color);
            padding: 12px 18px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            flex-shrink: 0;
        }
        .auto-assign-footer-counts {
            font-size: 0.85rem;
            font-weight: 700;
            color: var(--navy);
        }
        .auto-assign-footer-actions {
            display: flex;
            gap: 8px;
        }
        .auto-assign-skeleton {
            padding: 24px;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }
        .auto-assign-skeleton-box {
            background: #E2E8F0;
            border-radius: 2px;
            animation: aa-pulse 1.4s ease infinite;
        }
        @keyframes aa-pulse {
            0%, 100% { opacity: 0.6; }
            50% { opacity: 1; }
        }
        @media (max-width: 640px) {
            #autoAssignConfirmModal .modal-content {
                width: 100% !important;
                height: 100% !important;
                max-height: 100vh !important;
                max-width: 100% !important;
                border-radius: 0 !important;
                border: none !important;
                box-shadow: none !important;
            }
            .auto-assign-summary-strip {
                flex-wrap: wrap;
                font-size: 0.74rem;
                padding: 6px 10px;
            }
            .auto-assign-ticket-row {
                flex-direction: column;
                align-items: flex-start;
                gap: 6px;
            }
            .auto-assign-row-actions {
                width: 100%;
                justify-content: space-between;
            }
            .auto-assign-change-select {
                max-width: 100%;
                flex: 1 1 auto;
            }
        }
    `;
    document.head.appendChild(style);
}

/**
 * Opens the Auto-Assign Confirmation Modal.
 * Supports single ticket or group selection preview with live overrides, exclusions, and atomic confirmation.
 */
export async function openAutoAssignConfirmationModal(ticketIds, onRefresh) {
    const user = getCurrentUser();
    if (!user || user.role !== 'ADMIN') {
        showToast('Unauthorized: Only administrators can auto-assign tickets', 'error');
        return;
    }

    if (!ticketIds || ticketIds.length === 0) {
        showToast('No tickets selected for auto-assignment', 'warning');
        return;
    }

    ensureAutoAssignStyles();

    let modal = document.getElementById('autoAssignConfirmModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'autoAssignConfirmModal';
        modal.className = 'modal';
        modal.style.display = 'none';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.innerHTML = `
            <div class="modal-overlay" id="autoAssignOverlay"></div>
            <div class="modal-content" id="autoAssignModalContent">
                <div id="autoAssignModalBody"></div>
            </div>
        `;
        document.body.appendChild(modal);
    }

    const isSingle = ticketIds.length === 1;
    modal.classList.remove('modal-single', 'modal-group');
    modal.classList.add(isSingle ? 'modal-single' : 'modal-group');
    modal.style.display = 'flex';

    let inFlight = false;
    let keyHandler = null;

    const closeModal = () => {
        if (inFlight) return;
        modal.style.display = 'none';
        if (keyHandler) {
            document.removeEventListener('keydown', keyHandler);
            keyHandler = null;
        }
    };

    const modalBody = modal.querySelector('#autoAssignModalBody');
    modalBody.innerHTML = `
        <div class="auto-assign-header">
            <h3>Computing auto-assignment...</h3>
            <button type="button" class="modal-close" id="closeAutoAssignModal" aria-label="Close">✕</button>
        </div>
        <div class="auto-assign-skeleton">
            <div class="auto-assign-skeleton-box" style="width: 50%; height: 22px;"></div>
            <div class="auto-assign-skeleton-box" style="width: 100%; height: 50px;"></div>
            <div class="auto-assign-skeleton-box" style="width: 80%; height: 24px;"></div>
            <div class="auto-assign-skeleton-box" style="width: 95%; height: 45px;"></div>
            <p style="text-align: center; color: var(--navy); font-weight: 700; margin-top: 8px;">Evaluating specialization and staff workloads...</p>
        </div>
    `;

    modal.querySelector('#closeAutoAssignModal').onclick = closeModal;
    modal.querySelector('#autoAssignOverlay').onclick = closeModal;

    let previewData;
    try {
        previewData = await apiRequest('/assignments/auto/preview', {
            method: 'POST',
            body: { ticketIds }
        });
    } catch (err) {
        modalBody.innerHTML = `
            <div class="auto-assign-header">
                <h3>Auto-Assign Preview Failed</h3>
                <button type="button" class="modal-close" id="closeAutoAssignModal" aria-label="Close">✕</button>
            </div>
            <div style="padding: 24px; text-align: center;">
                <p style="color: var(--red); font-weight: 700; margin-bottom: 14px;">${escapeHtml(err.message)}</p>
                <button type="button" class="btn btn-secondary" id="errCloseBtn">Close</button>
            </div>
        `;
        modal.querySelector('#closeAutoAssignModal').onclick = closeModal;
        modal.querySelector('#errCloseBtn').onclick = closeModal;
        return;
    }

    const tickets = previewData.tickets || [];
    if (tickets.length === 0) {
        modalBody.innerHTML = `
            <div class="auto-assign-header">
                <h3>No Tickets Found</h3>
                <button type="button" class="modal-close" id="closeAutoAssignModal" aria-label="Close">✕</button>
            </div>
            <div style="padding: 24px; text-align: center;">
                <p style="color: var(--grey); margin-bottom: 14px;">The selected ticket(s) could not be located.</p>
                <button type="button" class="btn btn-secondary" id="emptyCloseBtn">Close</button>
            </div>
        `;
        modal.querySelector('#closeAutoAssignModal').onclick = closeModal;
        modal.querySelector('#emptyCloseBtn').onclick = closeModal;
        return;
    }

    let state = tickets.map(t => ({
        ticketId: t.ticketId,
        category: t.category,
        categoryDisplay: t.categoryDisplay,
        priority: t.priority,
        priorityLevel: t.priorityLevel,
        location: t.location,
        description: t.description,
        isAlreadyAssigned: t.isAlreadyAssigned,
        currentStaffName: t.currentStaffName,
        isOverdue: t.isOverdue,
        eligibleStaff: t.eligibleStaff || [],
        selectedStaffId: t.proposedStaff ? t.proposedStaff.staffId : null,
        selectedStaffName: t.proposedStaff ? t.proposedStaff.name : '',
        selectedStaffSpec: t.proposedStaff ? (t.proposedStaff.specializationDisplay || t.proposedStaff.specialization) : '',
        currentOpenTickets: t.proposedStaff ? t.proposedStaff.currentOpenTickets : 0,
        reason: t.proposedStaff ? t.proposedStaff.reason : (t.warnings && t.warnings[0] ? t.warnings[0] : 'No staff available'),
        warnings: t.warnings || [],
        isExcluded: !t.proposedStaff,
        conflictError: null
    }));

    const render = () => {
        if (state.length === 1) {
            renderSingleView();
        } else {
            renderGroupView();
        }
    };

    const renderSingleView = () => {
        const item = state[0];
        const pInfo = getPriorityInfo(item.priority);
        const pClass = pInfo.badgeClass || `priority-${String(item.priority).toLowerCase()}`;
        const pLabel = pInfo.label || item.priority;
        const isReassign = item.isAlreadyAssigned;

        modalBody.innerHTML = `
            <div class="auto-assign-header">
                <h3>Confirm assignment</h3>
                <button type="button" class="modal-close" id="closeAutoAssignModal" aria-label="Close">✕</button>
            </div>
            <div class="auto-assign-scroll-body">
                ${isReassign ? `
                    <div class="auto-assign-warnings">
                        <strong>Notice: Reassigning Ticket</strong>
                        This ticket is currently assigned to <strong>${escapeHtml(item.currentStaffName || 'a staff member')}</strong>. Confirming will reassign it.
                    </div>
                ` : ''}

                ${item.conflictError ? `
                    <div class="auto-assign-warnings" style="background:#FEF2F2; border-color:var(--red); color:var(--red);">
                        <strong>Assignment Conflict</strong>
                        ${escapeHtml(item.conflictError)}
                    </div>
                ` : ''}

                <div style="border: 2px solid var(--border-color); padding: 12px 14px; background: var(--bg-light); margin-bottom: 14px;">
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
                        <span class="auto-assign-ticket-id" style="font-size:0.95rem;">${escapeHtml(item.ticketId)}</span>
                        <span class="priority-badge ${pClass}">${escapeHtml(pLabel)}</span>
                    </div>
                    <div style="font-size:0.84rem; font-weight:700; color:var(--navy); margin-bottom:4px;">
                        ${escapeHtml(item.categoryDisplay || item.category)} · <span>${escapeHtml(item.location)}</span>
                    </div>
                    <div style="font-size:0.82rem; color:var(--navy); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
                        ${escapeHtml(item.description)}
                    </div>
                </div>

                <div style="border: 2px solid var(--border-color); padding: 14px; background: var(--white);">
                    <div style="font-size:0.75rem; text-transform:uppercase; font-weight:800; color:var(--grey); margin-bottom:6px; letter-spacing:0.5px;">Proposed Staff</div>
                    ${item.selectedStaffId ? `
                        <div style="display:flex; justify-content:space-between; align-items:flex-start;">
                            <div>
                                <strong style="font-size:1rem; color:var(--navy);">${escapeHtml(item.selectedStaffName)}</strong>
                                <div style="font-size:0.82rem; color:var(--grey); margin-top:2px;">
                                    ${escapeHtml(item.selectedStaffSpec)} · <strong>${item.currentOpenTickets} open tickets</strong>
                                </div>
                                <div style="font-size:0.82rem; color:var(--teal); font-weight:700; margin-top:4px;">
                                    ✓ ${escapeHtml(item.reason)}
                                </div>
                            </div>
                            <button type="button" class="btn btn-outline btn-sm" id="singleChangeStaffBtn" style="font-size:0.75rem;">Change staff</button>
                        </div>
                    ` : `
                        <div style="color:var(--red); font-weight:700; font-size:0.85rem;">
                            No eligible staff available for this ticket category.
                        </div>
                    `}

                    <div id="singleChangeDropdownWrap" style="display:none; margin-top:12px; padding-top:10px; border-top:1px dashed var(--border-color);">
                        <label for="singleSelectStaff" style="font-size:0.78rem; font-weight:700; display:block; margin-bottom:4px;">Select eligible staff override:</label>
                        <select class="form-control" id="singleSelectStaff" style="font-size:0.82rem; padding:4px 8px;">
                            ${item.eligibleStaff.map(s => `
                                <option value="${s.staffId}" ${s.staffId === item.selectedStaffId ? 'selected' : ''}>
                                    ${escapeHtml(s.name)} (${escapeHtml(s.specializationDisplay || s.specialization)}) - ${s.activeComplaints} open
                                </option>
                            `).join('')}
                        </select>
                    </div>
                </div>
            </div>
            <div class="auto-assign-footer">
                <div></div>
                <div class="auto-assign-footer-actions">
                    <button type="button" class="btn btn-secondary" id="singleCancelBtn">Cancel</button>
                    <button type="button" class="btn btn-primary" id="singleConfirmBtn" ${!item.selectedStaffId ? 'disabled' : ''}>
                        ${isReassign ? 'Reassign' : 'Assign'}
                    </button>
                </div>
            </div>
        `;

        modal.querySelector('#closeAutoAssignModal').onclick = closeModal;
        modal.querySelector('#autoAssignOverlay').onclick = closeModal;
        modal.querySelector('#singleCancelBtn').onclick = closeModal;

        const changeBtn = modal.querySelector('#singleChangeStaffBtn');
        const changeWrap = modal.querySelector('#singleChangeDropdownWrap');
        const selectStaff = modal.querySelector('#singleSelectStaff');

        if (changeBtn && changeWrap) {
            changeBtn.onclick = () => {
                changeWrap.style.display = changeWrap.style.display === 'none' ? 'block' : 'none';
            };
        }

        if (selectStaff) {
            selectStaff.onchange = () => {
                const sid = parseInt(selectStaff.value, 10);
                const matched = item.eligibleStaff.find(s => s.staffId === sid);
                if (matched) {
                    item.selectedStaffId = matched.staffId;
                    item.selectedStaffName = matched.name;
                    item.selectedStaffSpec = matched.specializationDisplay || matched.specialization;
                    item.currentOpenTickets = matched.activeComplaints;
                    item.reason = `${matched.specializationDisplay || matched.specialization} specialist (manual override)`;
                    render();
                }
            };
        }

        const confirmBtn = modal.querySelector('#singleConfirmBtn');
        confirmBtn.onclick = () => handleConfirm();
        confirmBtn.focus();

        setupKeyNav([confirmBtn, modal.querySelector('#singleCancelBtn')]);
    };

    const renderGroupView = () => {
        const totalCount = state.length;
        const activeCount = state.filter(t => !t.isExcluded && t.selectedStaffId).length;
        const excludedCount = totalCount - activeCount;
        const allAlreadyAssigned = state.every(t => t.isAlreadyAssigned);

        const priorityCounts = { URGENT: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
        let overdueCount = 0;
        const involvedStaff = new Set();

        state.forEach(t => {
            if (!t.isExcluded && t.selectedStaffId) {
                const prio = (t.priority || 'LOW').toUpperCase();
                priorityCounts[prio] = (priorityCounts[prio] || 0) + 1;
                if (t.isOverdue) overdueCount++;
                involvedStaff.add(t.selectedStaffId);
            }
        });

        const staffGroups = new Map();
        const unassignedTickets = [];

        state.forEach(t => {
            if (t.selectedStaffId) {
                if (!staffGroups.has(t.selectedStaffId)) {
                    staffGroups.set(t.selectedStaffId, {
                        staffId: t.selectedStaffId,
                        staffName: t.selectedStaffName,
                        specialization: t.selectedStaffSpec,
                        initialOpen: t.currentOpenTickets || 0,
                        tickets: []
                    });
                }
                staffGroups.get(t.selectedStaffId).tickets.push(t);
            } else {
                unassignedTickets.push(t);
            }
        });

        const noStaffCount = unassignedTickets.length;
        const capExceededStaff = Array.from(staffGroups.values()).filter(g => {
            const added = g.tickets.filter(t => !t.isExcluded).length;
            return (g.initialOpen + added) > 10;
        });
        const reassignCount = state.filter(t => !t.isExcluded && t.isAlreadyAssigned).length;
        const hasWarnings = noStaffCount > 0 || capExceededStaff.length > 0 || reassignCount > 0;

        let warningsHtml = '';
        if (hasWarnings) {
            warningsHtml = `
                <div class="auto-assign-warnings">
                    <strong>⚠️ Batch Warnings</strong>
                    <ul style="margin: 0; padding-left: 18px; line-height: 1.4;">
                        ${noStaffCount > 0 ? `
                            <li><span class="auto-assign-warning-link" data-warn="unassigned">${noStaffCount} ticket(s) have no eligible staff</span> (automatically excluded from batch).</li>
                        ` : ''}
                        ${capExceededStaff.map(g => `
                            <li><span class="auto-assign-warning-link" data-warn="staff-${g.staffId}">${escapeHtml(g.staffName)} would exceed workload cap</span> (${g.initialOpen} open + ${g.tickets.filter(t => !t.isExcluded).length} assigned = ${g.initialOpen + g.tickets.filter(t => !t.isExcluded).length} total).</li>
                        `).join('')}
                        ${reassignCount > 0 ? `
                            <li><span class="auto-assign-warning-link" data-warn="reassigned">${reassignCount} ticket(s) are already assigned</span> and will be reassigned.</li>
                        ` : ''}
                    </ul>
                </div>
            `;
        }

        modalBody.innerHTML = `
            <div class="auto-assign-header">
                <h3>${allAlreadyAssigned ? `Reassign ${totalCount} tickets` : `Confirm ${totalCount} assignments`}</h3>
                <button type="button" class="modal-close" id="closeAutoAssignModal" aria-label="Close">✕</button>
            </div>
            <div class="auto-assign-summary-strip">
                <span class="auto-assign-summary-stat"><strong>${totalCount}</strong> total</span>
                <span class="auto-assign-summary-stat">·</span>
                <span class="auto-assign-summary-stat"><strong>${involvedStaff.size}</strong> staff</span>
                <span class="auto-assign-summary-stat">·</span>
                <span class="auto-assign-summary-stat">
                    ${priorityCounts.URGENT > 0 ? `<span style="color:var(--red); font-weight:700;">U:${priorityCounts.URGENT}</span> ` : ''}
                    H:${priorityCounts.HIGH} M:${priorityCounts.MEDIUM} L:${priorityCounts.LOW}
                </span>
                <span class="auto-assign-summary-stat">·</span>
                <span class="auto-assign-summary-stat ${overdueCount > 0 ? 'auto-assign-summary-overdue' : ''}">
                    ${overdueCount > 0 ? `⚠️ ${overdueCount} overdue` : '0 overdue'}
                </span>
            </div>
            <div class="auto-assign-scroll-body" id="autoAssignScrollList">
                ${warningsHtml}

                ${Array.from(staffGroups.values()).map(g => {
                    const activeForStaff = g.tickets.filter(t => !t.isExcluded).length;
                    const projOpen = g.initialOpen + activeForStaff;
                    return `
                        <div class="auto-assign-staff-group" id="staff-group-${g.staffId}">
                            <div class="auto-assign-staff-header">
                                <div>
                                    <strong>👤 ${escapeHtml(g.staffName)}</strong> <span style="font-size:0.75rem; color:var(--grey);">(${escapeHtml(g.specialization)})</span>
                                </div>
                                <div class="auto-assign-staff-change">
                                    ${g.initialOpen} open → <strong>${projOpen} open</strong> (${activeForStaff} ticket${activeForStaff === 1 ? '' : 's'})
                                </div>
                            </div>
                            <div>
                                ${g.tickets.map(t => renderRowHtml(t)).join('')}
                            </div>
                        </div>
                    `;
                }).join('')}

                ${unassignedTickets.length > 0 ? `
                    <div class="auto-assign-staff-group" style="border-color:var(--amber);">
                        <div class="auto-assign-staff-header" style="background:#FFFBEB;">
                            <strong style="color:#7A4F01;">⚠️ No Eligible Staff Found (${unassignedTickets.length})</strong>
                            <span class="auto-assign-staff-change" style="color:#7A4F01;">Excluded automatically</span>
                        </div>
                        <div>
                            ${unassignedTickets.map(t => renderRowHtml(t)).join('')}
                        </div>
                    </div>
                ` : ''}
            </div>
            <div class="auto-assign-footer">
                <div class="auto-assign-footer-counts">
                    <span>${activeCount} will be assigned</span>, <span style="color:var(--grey);">${excludedCount} excluded</span>
                </div>
                <div class="auto-assign-footer-actions">
                    <button type="button" class="btn btn-secondary" id="groupCancelBtn">Cancel</button>
                    <button type="button" class="btn btn-primary" id="groupConfirmBtn" ${activeCount === 0 ? 'disabled' : ''}>
                        ${allAlreadyAssigned ? `Reassign all ${activeCount}` : `Accept all ${activeCount}`}
                    </button>
                </div>
            </div>
        `;

        modal.querySelector('#closeAutoAssignModal').onclick = closeModal;
        modal.querySelector('#autoAssignOverlay').onclick = closeModal;
        modal.querySelector('#groupCancelBtn').onclick = closeModal;

        modal.querySelectorAll('.auto-assign-warning-link').forEach(link => {
            link.onclick = () => {
                const target = link.getAttribute('data-warn');
                const scrollEl = modal.querySelector('#autoAssignScrollList');
                if (target.startsWith('staff-')) {
                    const gEl = modal.querySelector(`#${target}`);
                    if (gEl) gEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                } else if (target === 'reassigned') {
                    const row = modal.querySelector('.auto-assign-ticket-row[data-reassign="true"]');
                    if (row) row.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }
            };
        });

        attachRowEvents();

        const confirmBtn = modal.querySelector('#groupConfirmBtn');
        confirmBtn.onclick = () => handleConfirm();

        const rows = Array.from(modal.querySelectorAll('.auto-assign-ticket-row'));
        if (rows.length > 0) {
            rows[0].focus();
        }
        setupKeyNav(rows);
    };

    const renderRowHtml = (t) => {
        const pInfo = getPriorityInfo(t.priority);
        const pClass = pInfo.badgeClass || `priority-${String(t.priority).toLowerCase()}`;
        const pLabel = pInfo.label || t.priority;

        return `
            <div class="auto-assign-ticket-row ${t.isExcluded ? 'is-excluded' : ''} ${t.conflictError ? 'has-conflict' : ''}"
                 tabindex="0"
                 data-ticket-id="${t.ticketId}"
                 data-reassign="${t.isAlreadyAssigned}">
                <div class="auto-assign-row-main">
                    <div class="auto-assign-row-top">
                        <span class="auto-assign-ticket-id">${escapeHtml(t.ticketId)}</span>
                        <span class="priority-badge ${pClass}" style="font-size:0.72rem; padding:1px 5px;">${escapeHtml(pLabel)}</span>
                        <span style="font-size:0.75rem; color:var(--grey);">${escapeHtml(t.categoryDisplay || t.category)}</span>
                        ${t.isAlreadyAssigned ? `<span class="badge badge-amber" style="font-size:0.7rem; padding:1px 4px;">Reassign</span>` : ''}
                        ${t.isOverdue ? `<span class="badge badge-overdue" style="font-size:0.7rem; padding:1px 4px;">Overdue</span>` : ''}
                    </div>
                    <div class="auto-assign-row-desc" title="${escapeHtml(t.description)}">
                        ${escapeHtml(t.description)}
                    </div>
                    <div class="auto-assign-row-meta">
                        📍 ${escapeHtml(t.location)} · Reason: <span style="color:var(--teal); font-weight:600;">${escapeHtml(t.reason)}</span>
                    </div>
                    ${t.conflictError ? `
                        <div class="auto-assign-conflict-msg">⚠️ Conflict: ${escapeHtml(t.conflictError)}</div>
                    ` : ''}
                </div>
                <div class="auto-assign-row-actions">
                    <select class="auto-assign-change-select" data-ticket-id="${t.ticketId}" aria-label="Change assigned staff">
                        ${t.eligibleStaff.map(s => `
                            <option value="${s.staffId}" ${s.staffId === t.selectedStaffId ? 'selected' : ''}>
                                ${escapeHtml(s.name)} (${s.activeComplaints})
                            </option>
                        `).join('')}
                    </select>
                    ${t.isExcluded ? `
                        <button type="button" class="auto-assign-undo-btn" data-ticket-id="${t.ticketId}" title="Include in batch">Undo</button>
                    ` : `
                        <button type="button" class="auto-assign-remove-btn" data-ticket-id="${t.ticketId}" title="Exclude ticket from batch" aria-label="Exclude">✕</button>
                    `}
                </div>
            </div>
        `;
    };

    const attachRowEvents = () => {
        modal.querySelectorAll('.auto-assign-remove-btn').forEach(btn => {
            btn.onclick = (e) => {
                e.stopPropagation();
                const tid = btn.getAttribute('data-ticket-id');
                const match = state.find(t => t.ticketId === tid);
                if (match) {
                    match.isExcluded = true;
                    render();
                }
            };
        });

        modal.querySelectorAll('.auto-assign-undo-btn').forEach(btn => {
            btn.onclick = (e) => {
                e.stopPropagation();
                const tid = btn.getAttribute('data-ticket-id');
                const match = state.find(t => t.ticketId === tid);
                if (match) {
                    match.isExcluded = false;
                    render();
                }
            };
        });

        modal.querySelectorAll('.auto-assign-change-select').forEach(sel => {
            sel.onchange = (e) => {
                e.stopPropagation();
                const tid = sel.getAttribute('data-ticket-id');
                const newStaffId = parseInt(sel.value, 10);
                const match = state.find(t => t.ticketId === tid);
                if (match) {
                    const st = match.eligibleStaff.find(s => s.staffId === newStaffId);
                    if (st) {
                        match.selectedStaffId = st.staffId;
                        match.selectedStaffName = st.name;
                        match.selectedStaffSpec = st.specializationDisplay || st.specialization;
                        match.currentOpenTickets = st.activeComplaints;
                        match.reason = `${st.specializationDisplay || st.specialization} specialist (manual override)`;
                        render();
                    }
                }
            };
        });
    };

    const setupKeyNav = (focusableItems) => {
        if (keyHandler) {
            document.removeEventListener('keydown', keyHandler);
        }

        keyHandler = (e) => {
            if (e.key === 'Escape') {
                e.preventDefault();
                closeModal();
                return;
            }

            const rows = Array.from(modal.querySelectorAll('.auto-assign-ticket-row'));
            if (rows.length === 0) return;

            const activeIndex = rows.findIndex(r => r === document.activeElement || r.contains(document.activeElement));

            if (e.key === 'ArrowDown') {
                e.preventDefault();
                const next = (activeIndex + 1) % rows.length;
                rows[next].focus();
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                const prev = (activeIndex - 1 + rows.length) % rows.length;
                rows[prev].focus();
            } else if (e.key === ' ' && activeIndex >= 0 && !e.target.matches('select, button, input')) {
                e.preventDefault();
                const row = rows[activeIndex];
                const tid = row.getAttribute('data-ticket-id');
                const match = state.find(t => t.ticketId === tid);
                if (match) {
                    match.isExcluded = !match.isExcluded;
                    render();
                }
            }
        };

        document.addEventListener('keydown', keyHandler);
    };

    const handleConfirm = async () => {
        const activeList = state.filter(t => !t.isExcluded && t.selectedStaffId);
        if (activeList.length === 0) return;

        inFlight = true;
        const confirmBtn = modal.querySelector('#groupConfirmBtn') || modal.querySelector('#singleConfirmBtn');
        const cancelBtn = modal.querySelector('#groupCancelBtn') || modal.querySelector('#singleCancelBtn');
        if (confirmBtn) {
            confirmBtn.disabled = true;
            confirmBtn.textContent = `Assigning ${activeList.length} ticket${activeList.length === 1 ? '' : 's'}...`;
        }
        if (cancelBtn) cancelBtn.disabled = true;

        try {
            const assignmentsPayload = activeList.map(t => ({
                ticketId: t.ticketId,
                staffId: t.selectedStaffId
            }));

            const res = await apiRequest('/assignments/auto/confirm', {
                method: 'POST',
                body: { assignments: assignmentsPayload }
            });

            showToast(res.message || `${activeList.length} ticket(s) assigned successfully`, 'success');
            inFlight = false;
            closeModal();
            if (typeof onRefresh === 'function') {
                onRefresh();
            }
        } catch (err) {
            inFlight = false;
            if (confirmBtn) {
                confirmBtn.disabled = false;
                confirmBtn.textContent = state.length === 1 ? 'Assign' : `Retry assignment (${activeList.length})`;
            }
            if (cancelBtn) cancelBtn.disabled = false;

            let conflictList = [];
            try {
                if (err.data && err.data.conflicts) conflictList = err.data.conflicts;
            } catch (_) {}

            if (conflictList.length > 0) {
                conflictList.forEach(cf => {
                    const match = state.find(t => t.ticketId === cf.ticketId);
                    if (match) match.conflictError = cf.reason;
                });
                showToast('Stale data or assignment conflicts detected. Review marked rows.', 'error');
                render();
            } else {
                showToast('Assignment error: ' + err.message, 'error');
            }
        }
    };

    render();
}

function renderComplaintsTable(rawComplaints, wrapper, onRefresh, tableContext = 'triage') {
    if (!rawComplaints || rawComplaints.length === 0) {
        wrapper.innerHTML = `
            <div class="empty-state" style="padding: 40px 20px; text-align: center;">
                <div class="empty-icon" style="margin-bottom: 12px; color: var(--grey);">${getIcon('inbox', '', 40)}</div>
                <h3 style="margin-bottom: 6px;">No complaints found</h3>
                <p style="color: var(--grey); margin-bottom: 16px;">There are no complaints matching your current filters.</p>
            </div>
        `;
        return;
    }

    const sortMode = tableContext === 'triage' ? triageState.sort : allState.sort;
    const complaints = sortComplaints(rawComplaints, sortMode);
    const dupMap = detectDuplicates(complaints);

    const isAllSelected = complaints.length > 0 && complaints.every(c => selectedComplaintIds.has(c.complaintId));

    let html = '';

    // Bulk Action Bar if 1 or more items selected
    if (selectedComplaintIds.size > 0) {
        html += `
            <div class="bulk-action-bar" id="adminBulkBar">
                <span class="bulk-count"><strong>${selectedComplaintIds.size}</strong> complaint(s) selected</span>
                <div class="bulk-buttons">
                    <button class="btn btn-primary btn-sm" id="bulkAutoAssignBtn" type="button" title="Auto-assign selected complaints based on category and staff availability">Auto-Assign Selected</button>
                    <button class="btn btn-outline btn-sm bulk-action-btn" id="bulkManualAssignBtn" type="button">Assign Staff...</button>
                    <select class="bulk-priority-select" id="bulkPrioritySelect" aria-label="Set priority for selected complaints">
                        <option value="">Set Priority...</option>
                        <option value="1">High Priority</option>
                        <option value="2">Medium Priority</option>
                        <option value="3">Low Priority</option>
                    </select>
                    <button class="bulk-deselect-btn" id="bulkClearBtn" type="button">Deselect All</button>
                </div>
            </div>
        `;
    }

    html += `
        <div class="table-responsive complaints-table-wrapper">
            <table class="data-table">
                <thead>
                    <tr>
                        <th style="width: 4%; text-align: center;">
                            <input type="checkbox" id="adminSelectAllCb" class="table-checkbox" ${isAllSelected ? 'checked' : ''} aria-label="Select all complaints on current page">
                        </th>
                        <th style="width: 26%;">ID & Description</th>
                        <th style="width: 12%;">Priority & Category</th>
                        <th style="width: 16%;">Location & Student</th>
                        <th style="width: 12%;">Status</th>
                        <th style="width: 14%;">Assigned Staff</th>
                        <th style="width: 9%;">Due Date</th>
                        <th style="width: 7%; text-align: right;">Actions</th>
                    </tr>
                </thead>
                <tbody>
    `;

    complaints.forEach(c => {
        const pInfo = getPriorityInfo(c.priority);
        const pLabel = pInfo.label;
        const pClass = pInfo.class;
        const blockVal = (c.hostelBlock || c.studentHostelBlock || '').trim();
        const roomVal = (c.roomNumber || c.studentRoomNumber || '').trim();
        
        let locString = '';
        if (blockVal && roomVal) {
            locString = `${blockVal}-${roomVal}`;
        } else if (blockVal || roomVal) {
            locString = blockVal || roomVal;
        }

        const locationDisplay = locString ? escapeHtml(locString) : '<span style="color:var(--grey); font-style:italic;">N/A</span>';
        const studentName = c.studentName ? escapeHtml(c.studentName) : '';
        const isOverdue = Boolean(c.overdue || (c.dueBy && new Date(c.dueBy) < new Date() && c.status !== 'RESOLVED' && c.status !== 'CLOSED'));
        const isSelected = selectedComplaintIds.has(c.complaintId);

        // Duplicate check
        const dupKey = `${(c.category || '').trim().toUpperCase()}::${blockVal.toUpperCase()}::${roomVal.toUpperCase()}`;
        const dupList = (blockVal && roomVal && c.category) ? dupMap.get(dupKey) : null;
        const isDuplicate = dupList && dupList.length > 1;
        const otherDupId = isDuplicate ? dupList.find(id => id !== c.complaintId) : null;

        // Staff display
        let staffDisplay = '<span style="color:var(--grey); font-style:italic;">Unassigned</span>';
        if (c.staffName || c.assignedStaffName) {
            const name = c.staffName || c.assignedStaffName;
            const spec = c.assignedStaffSpecialization || c.specializationDisplay || '';
            staffDisplay = `<strong>${escapeHtml(name)}</strong>${spec ? ` <span style="font-size:0.8rem; color:var(--grey);">(${escapeHtml(spec)})</span>` : ''}`;
        }

        // Action buttons with compact sizing and secondary overflow menu
        let actionsHtml = '';
        if (c.status === 'NEW' && (!c.assignedStaffId && !c.staffId)) {
            actionsHtml = `
                <div class="triage-actions-wrap">
                    <button class="btn btn-primary btn-sm btn-assign btn-compact" data-id="${c.complaintId}" data-cat="${c.category}">Assign</button>
                    <div class="action-overflow-wrap">
                        <button type="button" class="btn btn-outline btn-sm btn-overflow-trigger" data-id="${c.complaintId}" title="More actions" aria-label="More actions">⋮</button>
                        <div class="action-overflow-menu" id="overflow-${c.complaintId}" style="display:none;">
                            <button type="button" class="action-overflow-item btn-auto" data-id="${c.complaintId}">Auto-assign</button>
                        </div>
                    </div>
                </div>
            `;
        } else if (c.status === 'REOPENED') {
            actionsHtml = `
                <div class="triage-actions-wrap">
                    <button class="btn btn-amber btn-sm btn-assign btn-compact" data-id="${c.complaintId}" data-cat="${c.category}">Assign</button>
                    <div class="action-overflow-wrap">
                        <button type="button" class="btn btn-outline btn-sm btn-overflow-trigger" data-id="${c.complaintId}" title="More actions" aria-label="More actions">⋮</button>
                        <div class="action-overflow-menu" id="overflow-${c.complaintId}" style="display:none;">
                            <button type="button" class="action-overflow-item btn-auto" data-id="${c.complaintId}">Auto-assign</button>
                        </div>
                    </div>
                </div>
            `;
        } else if (c.status === 'ASSIGNED' || c.status === 'IN_PROGRESS') {
            actionsHtml = `
                <div class="triage-actions-wrap">
                    <button class="btn btn-outline btn-sm btn-assign btn-compact" data-id="${c.complaintId}" data-cat="${c.category}">Reassign</button>
                </div>
            `;
        } else {
            actionsHtml = `<div class="triage-actions-wrap"><span style="color:var(--grey); font-size:0.85rem;">—</span></div>`;
        }

        // Status badge representation (No duplicate OVERDUE badge; due date & red row accent already indicate this)
        let statusBadgeHtml = '';
        if (c.status === 'NEW') {
            statusBadgeHtml = `<span class="status-badge status-NEW"><span class="status-dot"></span> New</span>`;
        } else {
            statusBadgeHtml = `<span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>`;
        }

        html += `
            <tr class="complaint-row ${isOverdue ? 'row-overdue' : ''} ${isSelected ? 'row-selected' : ''}" data-id="${c.complaintId}" style="cursor: pointer;">
                <td style="text-align: center;" onclick="event.stopPropagation();">
                    <input type="checkbox" class="complaint-select-cb" data-id="${c.complaintId}" ${isSelected ? 'checked' : ''} aria-label="Select complaint ${c.complaintId}">
                </td>
                <td data-label="ID & Description">
                    <div class="merged-id-desc">
                        <a href="#" class="complaint-link font-mono" data-id="${c.complaintId}">${escapeHtml(c.complaintId)}</a>
                        <div class="desc-line-truncate" title="${escapeHtml(c.description)}">${escapeHtml(c.description)}</div>
                    </div>
                </td>
                <td data-label="Priority & Category">
                    <div class="merged-priority-cat">
                        <span class="priority-badge ${pClass}">${escapeHtml(pLabel)}</span>
                        <span class="category-cell-wrap">
                            ${getCategoryIcon(c.category)}
                            <span>${escapeHtml(c.categoryDisplay || c.category)}</span>
                        </span>
                        ${isDuplicate ? `<span class="badge badge-duplicate" title="Potential duplicate of ${escapeHtml(otherDupId)} (same room & category)">⚠️ Dup</span>` : ''}
                    </div>
                </td>
                <td data-label="Location & Student">
                    <div class="compact-loc-student" title="Hostel ${escapeHtml(blockVal || 'Block')}, Room ${escapeHtml(roomVal || 'Room')} · ${studentName || 'Student'}">
                        <span class="loc-val">${locationDisplay}</span>
                        ${studentName ? `<span class="meta-dot">·</span><span class="student-val">${studentName}</span>` : ''}
                    </div>
                </td>
                <td data-label="Status">
                    ${statusBadgeHtml}
                </td>
                <td data-label="Assigned Staff" class="staff-cell">${staffDisplay}</td>
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

    // Attach row click listeners for detail drawer
    wrapper.querySelectorAll('.complaint-row').forEach(row => {
        row.addEventListener('click', (e) => {
            if (e.target.closest('.btn-assign') || e.target.closest('.btn-auto') || e.target.closest('.action-overflow-wrap') || e.target.closest('input[type="checkbox"]')) return;
            e.preventDefault();
            const id = row.getAttribute('data-id');
            openAdminComplaintDrawer(id, onRefresh);
        });
    });

    // Checkbox listeners
    const selectAllCb = wrapper.querySelector('#adminSelectAllCb');
    if (selectAllCb) {
        selectAllCb.addEventListener('change', (e) => {
            const checked = e.target.checked;
            complaints.forEach(c => {
                if (checked) {
                    selectedComplaintIds.add(c.complaintId);
                } else {
                    selectedComplaintIds.delete(c.complaintId);
                }
            });
            renderComplaintsTable(rawComplaints, wrapper, onRefresh, tableContext);
        });
    }

    wrapper.querySelectorAll('.complaint-select-cb').forEach(cb => {
        cb.addEventListener('change', (e) => {
            const id = cb.getAttribute('data-id');
            if (e.target.checked) {
                selectedComplaintIds.add(id);
            } else {
                selectedComplaintIds.delete(id);
            }
            renderComplaintsTable(rawComplaints, wrapper, onRefresh, tableContext);
        });
    });

    // Bulk action handlers
    const bulkAutoBtn = wrapper.querySelector('#bulkAutoAssignBtn');
    if (bulkAutoBtn) {
        bulkAutoBtn.onclick = () => {
            const ids = Array.from(selectedComplaintIds);
            if (ids.length === 0) return;
            openAutoAssignConfirmationModal(ids, () => {
                selectedComplaintIds.clear();
                if (typeof onRefresh === 'function') onRefresh();
            });
        };
    }

    const bulkManualBtn = wrapper.querySelector('#bulkManualAssignBtn');
    if (bulkManualBtn) {
        bulkManualBtn.onclick = () => {
            const ids = Array.from(selectedComplaintIds);
            if (ids.length === 0) return;
            // Open assign modal targeting the first item, refreshing all on completion
            openAssignModal(ids[0], null, () => {
                selectedComplaintIds.clear();
                if (typeof onRefresh === 'function') onRefresh();
            });
        };
    }

    const bulkPrioritySelect = wrapper.querySelector('#bulkPrioritySelect');
    if (bulkPrioritySelect) {
        bulkPrioritySelect.onchange = async () => {
            const val = parseInt(bulkPrioritySelect.value, 10);
            if (!val) return;
            const ids = Array.from(selectedComplaintIds);
            if (ids.length === 0) return;
            try {
                await Promise.allSettled(ids.map(id => apiRequest(`/admin/complaints/${id}/priority`, {
                    method: 'PUT',
                    body: { priority: val }
                })));
                showToast(`Updated priority for ${ids.length} complaint(s)!`, 'success');
                selectedComplaintIds.clear();
                if (typeof onRefresh === 'function') onRefresh();
            } catch (err) {
                showToast('Bulk priority update error: ' + err.message, 'error');
            }
        };
    }

    const bulkClearBtn = wrapper.querySelector('#bulkClearBtn');
    if (bulkClearBtn) {
        bulkClearBtn.onclick = () => {
            selectedComplaintIds.clear();
            renderComplaintsTable(rawComplaints, wrapper, onRefresh, tableContext);
        };
    }

    // Overflow menu toggle listeners
    wrapper.querySelectorAll('.btn-overflow-trigger').forEach(trigger => {
        trigger.addEventListener('click', (e) => {
            e.stopPropagation();
            const id = trigger.getAttribute('data-id');
            const menu = wrapper.querySelector(`#overflow-${id}`);
            wrapper.querySelectorAll('.action-overflow-menu').forEach(m => {
                if (m !== menu) m.style.display = 'none';
            });
            if (menu) {
                menu.style.display = menu.style.display === 'none' ? 'block' : 'none';
            }
        });
    });

    if (!window._hcmsOverflowClickBound) {
        window._hcmsOverflowClickBound = true;
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.action-overflow-wrap')) {
                document.querySelectorAll('.action-overflow-menu').forEach(m => {
                    m.style.display = 'none';
                });
            }
        });
    }

    // Individual action buttons
    wrapper.querySelectorAll('.btn-assign').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const id = btn.getAttribute('data-id');
            const cat = btn.getAttribute('data-cat');
            openAssignModal(id, cat, onRefresh);
        });
    });

    wrapper.querySelectorAll('.btn-auto').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const id = btn.getAttribute('data-id');
            // Close any open overflow menu
            const menu = wrapper.querySelector(`#overflow-${id}`);
            if (menu) menu.style.display = 'none';
            openAutoAssignConfirmationModal([id], onRefresh);
        });
    });
}

/**
 * Optimized Admin Complaint Detail Drawer.
 * Includes top-placed staff assignment, SLA countdown timer,
 * interactive lifecycle stepper, potential duplicate alert, and timeline auditing.
 */
export async function openAdminComplaintDrawer(complaintId, onRefresh) {
    const modal = document.getElementById('complaintModal');
    const detailEl = document.getElementById('complaintDetail');
    const closeBtn = document.getElementById('closeModal');
    const overlay = document.getElementById('drawerOverlay') || modal?.querySelector('.modal-overlay');

    if (!modal || !detailEl) return;

    modal.style.display = 'flex';
    modal.classList.add('open');
    detailEl.innerHTML = '<div class="loading">Loading complaint details...</div>';

    if (closeBtn) closeBtn.onclick = closeComplaintDrawer;
    if (overlay) overlay.onclick = closeComplaintDrawer;

    try {
        const [complaint, historyRes, commentsRes, staffRes] = await Promise.all([
            apiRequest(`/complaints/${complaintId}`),
            apiRequest(`/complaints/${complaintId}/history`),
            apiRequest(`/complaints/${complaintId}/comments`),
            apiRequest('/admin/staff?activeOnly=true').catch(() => ({ staff: [] }))
        ]);

        const historyList = (historyRes && Array.isArray(historyRes.history))
            ? historyRes.history
            : (Array.isArray(historyRes) ? historyRes : (complaint && Array.isArray(complaint.history) ? complaint.history : []));

        const commentsList = (commentsRes && Array.isArray(commentsRes.comments))
            ? commentsRes.comments
            : (Array.isArray(commentsRes) ? commentsRes : (complaint && Array.isArray(complaint.comments) ? complaint.comments : []));

        const staffList = (staffRes && Array.isArray(staffRes.staff)) ? staffRes.staff : (Array.isArray(staffRes) ? staffRes : []);

        renderAdminDetailDrawer(complaint, historyList, commentsList, staffList, onRefresh);
    } catch (err) {
        detailEl.innerHTML = `<div class="toast toast-error">${escapeHtml(err.message)}</div>`;
    }
}

function renderAdminDetailDrawer(c, history, comments, staffList, onRefresh) {
    const detailEl = document.getElementById('complaintDetail');
    if (!detailEl) return;

    const priInfo = getPriorityInfo(c.priority);
    const blockVal = c.studentHostelBlock || c.hostelBlock || '';
    const roomVal = c.studentRoomNumber || c.roomNumber || '';
    const locText = (blockVal && roomVal) ? `Block ${blockVal} · Room ${roomVal}` : (blockVal || roomVal || 'Unspecified');

    // SLA Countdown calculation
    let slaTimerHtml = '';
    const dueTime = c.dueBy ? new Date(c.dueBy).getTime() : null;
    const now = Date.now();

    if (c.status === 'RESOLVED' || c.status === 'CLOSED') {
        slaTimerHtml = `
            <div class="drawer-sla-timer" style="background:#F0FDF4; border-color:#BBF7D0; color:#166534;">
                <span>✅ <strong>Resolution Completed</strong> · Ticket is closed/resolved</span>
                <span class="sla-timer-countdown">Resolved</span>
            </div>
        `;
    } else if (dueTime) {
        if (now > dueTime) {
            const diffMs = now - dueTime;
            const hours = Math.floor(diffMs / (1000 * 60 * 60));
            const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
            slaTimerHtml = `
                <div class="drawer-sla-timer overdue">
                    <span>🚨 <strong>SLA Breached</strong> · Target was ${formatExactDate(c.dueBy)}</span>
                    <span class="sla-timer-countdown">Overdue by ${hours > 0 ? `${hours}h ` : ''}${mins}m</span>
                </div>
            `;
        } else {
            const diffMs = dueTime - now;
            const hours = Math.floor(diffMs / (1000 * 60 * 60));
            const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
            slaTimerHtml = `
                <div class="drawer-sla-timer">
                    <span>⏱️ <strong>SLA Target</strong> · Target is ${formatExactDate(c.dueBy)}</span>
                    <span class="sla-timer-countdown">Due in ${hours > 0 ? `${hours}h ` : ''}${mins}m</span>
                </div>
            `;
        }
    }

    // Top-placed Staff Assignment Card
    let staffAssignmentCard = '';
    if (!c.staffId && !c.assignedStaffId) {
        staffAssignmentCard = `
            <div class="drawer-assignment-card unassigned">
                <div class="drawer-assignment-info">
                    <div class="drawer-assignment-title">Staff Assignment · Action Required</div>
                    <div class="drawer-assignment-name" style="color: #B45309;">⚠️ Unassigned Complaint</div>
                </div>
                <div class="drawer-assignment-actions">
                    <select id="drawerStaffSelect" class="toolbar-select" style="font-size:0.8rem; padding:4px 8px;">
                        <option value="">Select staff...</option>
                        ${staffList.map(s => `<option value="${s.userId}">${escapeHtml(s.name)} (${escapeHtml(s.specializationDisplay || s.specialization || 'General')})</option>`).join('')}
                    </select>
                    <button class="btn btn-primary btn-sm" id="drawerAssignBtn" disabled>Assign</button>
                    <button class="btn btn-outline btn-sm" id="drawerAutoAssignBtn" title="Auto-assign based on specialization and workload">🤖 Auto-Assign</button>
                </div>
            </div>
        `;
    } else {
        const staffName = c.assignedStaffName || c.staffName || `Staff #${c.staffId}`;
        const staffSpec = c.assignedStaffSpecialization || 'Maintenance Specialist';
        staffAssignmentCard = `
            <div class="drawer-assignment-card">
                <div class="drawer-assignment-info">
                    <div class="drawer-assignment-title">Assigned Staff</div>
                    <div class="drawer-assignment-name">👤 ${escapeHtml(staffName)} <span style="font-size:0.8rem; color:var(--grey); font-weight:normal;">(${escapeHtml(staffSpec)})</span></div>
                </div>
                <div class="drawer-assignment-actions">
                    <button class="btn btn-outline btn-sm" id="drawerReassignToggleBtn">Reassign...</button>
                    <div id="drawerReassignWrap" style="display:none; align-items:center; gap:6px;">
                        <select id="drawerReassignSelect" class="toolbar-select" style="font-size:0.8rem; padding:4px 8px;">
                            <option value="">Select new staff...</option>
                            ${staffList.map(s => `<option value="${s.userId}">${escapeHtml(s.name)} (${escapeHtml(s.specializationDisplay || s.specialization || 'General')})</option>`).join('')}
                        </select>
                        <button class="btn btn-primary btn-sm" id="drawerReassignSubmitBtn" disabled>Save</button>
                    </div>
                </div>
            </div>
        `;
    }

    // Interactive Lifecycle Stepper
    const stepOrder = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
    const currentStepIdx = stepOrder.indexOf(c.status);
    const isReopened = c.status === 'REOPENED';

    const stepperHtml = `
        <div class="interactive-stepper">
            <div class="stepper-step ${currentStepIdx >= 0 && !isReopened ? (currentStepIdx > 0 ? 'completed' : 'active') : ''}">
                <div class="stepper-node">${currentStepIdx > 0 && !isReopened ? '✓' : '1'}</div>
                <div class="stepper-label">New</div>
            </div>
            <div class="stepper-step ${isReopened ? 'reopened' : (currentStepIdx >= 1 ? (currentStepIdx > 1 ? 'completed' : 'active') : '')}">
                <div class="stepper-node">${isReopened ? '!' : (currentStepIdx > 1 ? '✓' : '2')}</div>
                <div class="stepper-label">${isReopened ? 'Reopened' : 'Assigned'}</div>
            </div>
            <div class="stepper-step ${currentStepIdx >= 2 && !isReopened ? (currentStepIdx > 2 ? 'completed' : 'active') : ''}">
                <div class="stepper-node">${currentStepIdx > 2 && !isReopened ? '✓' : '3'}</div>
                <div class="stepper-label">In Progress</div>
            </div>
            <div class="stepper-step ${currentStepIdx >= 3 && !isReopened ? (currentStepIdx > 3 ? 'completed' : 'active') : ''}">
                <div class="stepper-node">${currentStepIdx > 3 && !isReopened ? '✓' : '4'}</div>
                <div class="stepper-label">Resolved</div>
            </div>
            <div class="stepper-step ${currentStepIdx === 4 ? 'completed' : ''}">
                <div class="stepper-node">${currentStepIdx === 4 ? '✓' : '5'}</div>
                <div class="stepper-label">Closed</div>
            </div>
        </div>
    `;

    // Action buttons
    const allowed = c.allowedActions || [];
    let actionButtonsHtml = '';
    const actionBtns = [];
    if (allowed.includes('CLOSE')) {
        actionBtns.push('<button class="btn btn-teal btn-sm" id="btnAdminActionClose">Close Complaint</button>');
    }
    if (allowed.includes('REOPEN')) {
        actionBtns.push('<button class="btn btn-amber btn-sm" id="btnAdminActionReopen">Reopen Complaint</button>');
    }
    if (actionBtns.length > 0) {
        actionButtonsHtml = `<div class="form-actions" style="margin-top: 14px; display:flex; gap:8px;">${actionBtns.join('')}</div>`;
    }

    // Student Info Card
    const studentInfoHtml = c.studentName ? `
        <div class="student-detail-card" style="margin: 12px 0;">
            <div class="student-detail-header">
                <span class="student-detail-title"><span>👤</span> Student Details</span>
                <span class="student-badge">Reporter</span>
            </div>
            <div class="student-detail-grid">
                <div class="student-detail-item">
                    <span class="student-detail-label">Name</span>
                    <span class="student-detail-value">${escapeHtml(c.studentName)}</span>
                </div>
                <div class="student-detail-item">
                    <span class="student-detail-label">Location</span>
                    <span class="student-detail-value"><span class="meta-tag">🏢 ${escapeHtml(locText)}</span></span>
                </div>
                ${c.studentEmail ? `
                    <div class="student-detail-item" style="grid-column: 1 / -1;">
                        <span class="student-detail-label">Email</span>
                        <span class="student-detail-value"><a href="mailto:${escapeHtml(c.studentEmail)}" style="color:var(--navy); text-decoration:underline;">${escapeHtml(c.studentEmail)}</a></span>
                    </div>
                ` : ''}
            </div>
        </div>
    ` : '';

    // Audit Timeline
    let timelineHtml = '<div class="timeline">';
    if (history && history.length > 0) {
        history.forEach(h => {
            const author = h.changedByName ? ` • ${escapeHtml(h.changedByName)}` : '';
            timelineHtml += `
                <div class="timeline-item">
                    <div class="timeline-time">${formatDate(h.changedAt)}${author}</div>
                    <div class="timeline-title">${escapeHtml(h.oldStatus ? `${formatStatus(h.oldStatus)} ➔ ${formatStatus(h.newStatus)}` : formatStatus(h.newStatus))}</div>
                    ${h.note ? `<div class="timeline-note">📝 ${escapeHtml(h.note)}</div>` : ''}
                </div>
            `;
        });
    } else {
        timelineHtml += '<p class="text-muted">No timeline entries recorded.</p>';
    }
    timelineHtml += '</div>';

    // Comments list
    let commentsHtml = '<div class="comments-section" style="margin-top:20px;"><h4>💬 Internal & Student Discussion</h4><div id="commentsList">';
    if (comments && comments.length > 0) {
        comments.forEach(cm => {
            commentsHtml += `
                <div class="comment-item">
                    <div class="comment-header">
                        <span class="comment-author">${escapeHtml(cm.authorName || 'User')} (${escapeHtml(cm.authorRole || 'USER')})</span>
                        <span>${formatDate(cm.createdAt)}</span>
                    </div>
                    <p>${escapeHtml(cm.message)}</p>
                </div>
            `;
        });
    } else {
        commentsHtml += '<p style="color: var(--grey); font-size: 0.85rem; margin-bottom: 10px;">No comments yet.</p>';
    }
    commentsHtml += `</div>
        <form id="drawerAddCommentForm" style="display:flex; gap: 8px; margin-top: 12px;">
            <input type="text" id="drawerCommentInput" placeholder="Add a note or reply..." required style="flex:1; padding:7px 10px; font-size:0.85rem; border:1.5px solid var(--border-color);">
            <button type="submit" class="btn btn-outline btn-sm">Send</button>
        </form>
    </div>`;

    detailEl.innerHTML = `
        <div style="display:flex; justify-content:space-between; align-items:center; border-bottom:2px solid var(--border-color); padding-bottom:12px; margin-bottom:14px; padding-right:48px; flex-wrap:wrap; gap:8px;">
            <div>
                <span class="font-mono" style="font-weight:800; font-size:1.15rem; color:var(--navy);">${escapeHtml(c.complaintId)}</span>
                <span style="margin-left:8px; font-size:0.85rem; color:var(--grey);">${getCategoryIcon(c.category)} ${escapeHtml(c.categoryDisplay || c.category)}</span>
            </div>
            <div style="display:flex; gap:8px; align-items:center; flex-wrap:wrap;">
                <div style="display:flex; align-items:center; gap:4px;">
                    <span style="font-size:0.75rem; font-weight:700;">Priority:</span>
                    <select id="detailPrioritySelect" class="toolbar-select" style="font-size:0.8rem; padding:3px 6px;">
                        <option value="1" ${priInfo.key === 'HIGH' ? 'selected' : ''}>🔴 High</option>
                        <option value="2" ${priInfo.key === 'MEDIUM' ? 'selected' : ''}>🟠 Medium</option>
                        <option value="3" ${priInfo.key === 'LOW' ? 'selected' : ''}>🟢 Low</option>
                    </select>
                </div>
                <span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>
            </div>
        </div>

        ${staffAssignmentCard}
        ${slaTimerHtml}
        ${stepperHtml}
        ${actionButtonsHtml}
        ${studentInfoHtml}

        <div style="margin: 14px 0;">
            <div style="font-size:0.75rem; font-weight:700; text-transform:uppercase; color:var(--grey); margin-bottom:4px;">Description</div>
            <div style="font-size:0.95rem; line-height:1.5; color:var(--navy); background:#F8FAFC; padding:10px 12px; border:1px solid #E2E8F0;">
                ${escapeHtml(c.description)}
            </div>
        </div>

        ${c.attachments && c.attachments.length > 0 ? `
            <div style="margin: 12px 0; padding: 10px 14px; background: #F8FAFC; border: 1.5px solid var(--border-color);">
                <div style="font-weight: 700; font-size: 0.85rem; margin-bottom: 6px;">📷 Attachments (${c.attachments.length})</div>
                ${c.attachments.map(att => `<div style="display: flex; align-items: center; gap: 8px; font-size: 0.82rem;"><span>📎</span><span style="font-family: var(--font-mono); font-weight: 600;">${escapeHtml(att.fileName)}</span><span style="color: var(--grey); font-size: 0.75rem;">(${Math.round((att.sizeBytes || 0) / 1024)} KB)</span></div>`).join('')}
            </div>
        ` : ''}

        <h4 style="margin-top: 24px;">📜 Audit Timeline</h4>
        ${timelineHtml}
        ${commentsHtml}
    `;

    // Event bindings in drawer:
    // 1. Priority change
    const priSelect = document.getElementById('detailPrioritySelect');
    if (priSelect) {
        priSelect.onchange = async () => {
            const newPri = parseInt(priSelect.value, 10);
            try {
                await apiRequest(`/admin/complaints/${c.complaintId}/priority`, {
                    method: 'PUT',
                    body: { priority: newPri }
                });
                showToast(`Priority updated for ${c.complaintId}`, 'success');
                openAdminComplaintDrawer(c.complaintId, onRefresh);
                if (typeof onRefresh === 'function') onRefresh();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    // 2. Unassigned staff assignment
    const staffSelect = document.getElementById('drawerStaffSelect');
    const assignBtn = document.getElementById('drawerAssignBtn');
    if (staffSelect && assignBtn) {
        staffSelect.onchange = () => {
            assignBtn.disabled = !staffSelect.value;
        };
        assignBtn.onclick = async () => {
            const staffId = staffSelect.value;
            if (!staffId) return;
            try {
                await apiRequest(`/admin/complaints/${c.complaintId}/assign`, {
                    method: 'POST',
                    body: { staffId: parseInt(staffId, 10) }
                });
                showToast(`Complaint assigned to staff #${staffId}`, 'success');
                openAdminComplaintDrawer(c.complaintId, onRefresh);
                if (typeof onRefresh === 'function') onRefresh();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    const autoAssignBtn = document.getElementById('drawerAutoAssignBtn');
    if (autoAssignBtn) {
        autoAssignBtn.onclick = () => {
            openAutoAssignConfirmationModal([c.complaintId], () => {
                openAdminComplaintDrawer(c.complaintId, onRefresh);
                if (typeof onRefresh === 'function') onRefresh();
            });
        };
    }

    // 3. Reassign staff toggle
    const reassignToggleBtn = document.getElementById('drawerReassignToggleBtn');
    const reassignWrap = document.getElementById('drawerReassignWrap');
    const reassignSelect = document.getElementById('drawerReassignSelect');
    const reassignSubmitBtn = document.getElementById('drawerReassignSubmitBtn');
    if (reassignToggleBtn && reassignWrap && reassignSelect && reassignSubmitBtn) {
        reassignToggleBtn.onclick = () => {
            reassignWrap.style.display = reassignWrap.style.display === 'none' ? 'flex' : 'none';
        };
        reassignSelect.onchange = () => {
            reassignSubmitBtn.disabled = !reassignSelect.value;
        };
        reassignSubmitBtn.onclick = async () => {
            const newStaffId = reassignSelect.value;
            if (!newStaffId) return;
            try {
                await apiRequest(`/admin/complaints/${c.complaintId}/assign`, {
                    method: 'POST',
                    body: { staffId: parseInt(newStaffId, 10) }
                });
                showToast(`Complaint reassigned to staff #${newStaffId}`, 'success');
                openAdminComplaintDrawer(c.complaintId, onRefresh);
                if (typeof onRefresh === 'function') onRefresh();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    // 4. Close & Reopen buttons
    const btnClose = document.getElementById('btnAdminActionClose');
    if (btnClose) {
        btnClose.onclick = async () => {
            if (!confirm(`Are you sure you want to close complaint ${c.complaintId}?`)) return;
            try {
                await apiRequest(`/complaints/${c.complaintId}/close`, { method: 'POST' });
                showToast('Complaint closed', 'success');
                openAdminComplaintDrawer(c.complaintId, onRefresh);
                if (typeof onRefresh === 'function') onRefresh();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    const btnReopen = document.getElementById('btnAdminActionReopen');
    if (btnReopen) {
        btnReopen.onclick = async () => {
            const reason = prompt('Please enter the reason for reopening this complaint:');
            if (!reason) return;
            try {
                await apiRequest(`/complaints/${c.complaintId}/reopen`, {
                    method: 'POST',
                    body: { reason }
                });
                showToast('Complaint reopened', 'success');
                openAdminComplaintDrawer(c.complaintId, onRefresh);
                if (typeof onRefresh === 'function') onRefresh();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    // 5. Add comment
    const commentForm = document.getElementById('drawerAddCommentForm');
    if (commentForm) {
        commentForm.onsubmit = async (e) => {
            e.preventDefault();
            const input = document.getElementById('drawerCommentInput');
            if (!input || !input.value.trim()) return;
            try {
                await apiRequest(`/complaints/${c.complaintId}/comments`, {
                    method: 'POST',
                    body: { message: input.value.trim() }
                });
                showToast('Comment posted', 'success');
                openAdminComplaintDrawer(c.complaintId, onRefresh);
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }
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

        renderComplaintsTable(complaints, wrapper, () => loadTriageBoard(), 'triage');
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

        // Populate 4-card summary strip for All Complaints
        Promise.all([
            apiRequest('/admin/analytics').catch(() => null),
            apiRequest('/admin/triage?size=1').catch(() => null)
        ]).then(([analytics, triage]) => {
            if (triage && triage.counters) {
                const elOverdue = document.getElementById('allCountOverdue');
                const elUnassigned = document.getElementById('allCountUnassigned');
                if (elOverdue) elOverdue.textContent = triage.counters.overdue ?? 0;
                if (elUnassigned) elUnassigned.textContent = triage.counters.unassigned ?? 0;
            }
            if (analytics && analytics.byStatus) {
                const elInProgress = document.getElementById('allCountInProgress');
                const elResolvedToday = document.getElementById('allCountResolvedToday');
                if (elInProgress) elInProgress.textContent = analytics.byStatus.IN_PROGRESS ?? 0;
                if (elResolvedToday) elResolvedToday.textContent = analytics.byStatus.RESOLVED ?? 0;
            }
        });

        renderComplaintsTable(complaints, wrapper, () => loadAdminComplaints(), 'all');
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
        autoBtn.onclick = () => {
            modal.style.display = 'none';
            openAutoAssignConfirmationModal([complaintId], () => {
                triggerRefresh();
            });
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

let userManagementSetup = false;
let currentStaff = [];
let currentStudents = [];
const studentState = {
    q: '',
    page: 0,
    size: 10
};

export function loadUserManagementView() {
    loadStaffList();
    loadStudentList();
}

export function setupUserManagement() {
    if (userManagementSetup) return;
    userManagementSetup = true;

    // Sub-tab toggling (Staff vs Students)
    const tabStaffSub = document.getElementById('tabStaffSub');
    const tabStudentSub = document.getElementById('tabStudentSub');
    const subPanelStaff = document.getElementById('subPanelStaff');
    const subPanelStudents = document.getElementById('subPanelStudents');

    if (tabStaffSub && tabStudentSub && subPanelStaff && subPanelStudents) {
        tabStaffSub.onclick = () => {
            tabStaffSub.classList.add('active');
            tabStudentSub.classList.remove('active');
            subPanelStaff.style.display = 'block';
            subPanelStudents.style.display = 'none';
        };

        tabStudentSub.onclick = () => {
            tabStudentSub.classList.add('active');
            tabStaffSub.classList.remove('active');
            subPanelStaff.style.display = 'none';
            subPanelStudents.style.display = 'block';
            if (currentStudents.length === 0) {
                loadStudentList();
            }
        };
    }

    // Add Staff modal triggers
    const addStaffBtn = document.getElementById('btnAdminAddStaff');
    const addStaffModal = document.getElementById('adminAddStaffModal');
    const closeAddStaffModal = document.getElementById('closeAddStaffModal');
    const cancelAddStaffBtn = document.getElementById('cancelAddStaffBtn');
    const addStaffOverlay = document.getElementById('addStaffOverlay');
    const addStaffForm = document.getElementById('adminAddStaffForm');

    const closeAddStaff = () => { if (addStaffModal) addStaffModal.style.display = 'none'; };
    if (addStaffBtn && addStaffModal) {
        addStaffBtn.onclick = () => {
            if (addStaffForm) addStaffForm.reset();
            addStaffModal.style.display = 'flex';
        };
    }
    if (closeAddStaffModal) closeAddStaffModal.onclick = closeAddStaff;
    if (cancelAddStaffBtn) cancelAddStaffBtn.onclick = closeAddStaff;
    if (addStaffOverlay) addStaffOverlay.onclick = closeAddStaff;
    if (addStaffForm) {
        addStaffForm.onsubmit = async (e) => {
            e.preventDefault();
            const name = document.getElementById('addStaffName').value.trim();
            const email = document.getElementById('addStaffEmail').value.trim();
            const password = document.getElementById('addStaffPassword').value.trim();
            const specialization = document.getElementById('addStaffSpecialization').value;

            if (!name || !email) {
                showToast('Name and email are required', 'error');
                return;
            }

            try {
                await apiRequest('/admin/staff', {
                    method: 'POST',
                    body: {
                        name,
                        email,
                        password: password || 'password123',
                        specialization
                    }
                });
                showToast('Maintenance staff created successfully!', 'success');
                closeAddStaff();
                loadStaffList();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    // Edit Staff modal listeners
    const editStaffModal = document.getElementById('adminEditStaffModal');
    const closeEditStaffModal = document.getElementById('closeEditStaffModal');
    const cancelEditStaffBtn = document.getElementById('cancelEditStaffBtn');
    const editStaffOverlay = document.getElementById('editStaffOverlay');
    const editStaffForm = document.getElementById('adminEditStaffForm');

    const closeEditStaff = () => { if (editStaffModal) editStaffModal.style.display = 'none'; };
    if (closeEditStaffModal) closeEditStaffModal.onclick = closeEditStaff;
    if (cancelEditStaffBtn) cancelEditStaffBtn.onclick = closeEditStaff;
    if (editStaffOverlay) editStaffOverlay.onclick = closeEditStaff;
    if (editStaffForm) {
        editStaffForm.onsubmit = async (e) => {
            e.preventDefault();
            const userId = document.getElementById('editStaffUserId').value;
            const name = document.getElementById('editStaffName').value.trim();
            const specialization = document.getElementById('editStaffSpecialization').value;
            const available = document.getElementById('editStaffAvailable').checked;
            const active = document.getElementById('editStaffActive').checked;

            if (!name) {
                showToast('Staff name cannot be blank', 'error');
                return;
            }

            try {
                await apiRequest(`/admin/users/${userId}`, {
                    method: 'PUT',
                    body: {
                        name,
                        specialization,
                        available,
                        active
                    }
                });
                showToast('Staff member updated successfully!', 'success');
                closeEditStaff();
                loadStaffList();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    // Edit Student modal listeners
    const editStudentModal = document.getElementById('adminEditStudentModal');
    const closeEditStudentModal = document.getElementById('closeEditStudentModal');
    const cancelEditStudentBtn = document.getElementById('cancelEditStudentBtn');
    const editStudentOverlay = document.getElementById('editStudentOverlay');
    const editStudentForm = document.getElementById('adminEditStudentForm');

    const closeEditStudent = () => { if (editStudentModal) editStudentModal.style.display = 'none'; };
    if (closeEditStudentModal) closeEditStudentModal.onclick = closeEditStudent;
    if (cancelEditStudentBtn) cancelEditStudentBtn.onclick = closeEditStudent;
    if (editStudentOverlay) editStudentOverlay.onclick = closeEditStudent;
    if (editStudentForm) {
        editStudentForm.onsubmit = async (e) => {
            e.preventDefault();
            const userId = document.getElementById('editStudentUserId').value;
            const name = document.getElementById('editStudentName').value.trim();
            const hostelBlock = document.getElementById('editStudentBlock').value.trim().toUpperCase();
            const roomNumber = document.getElementById('editStudentRoom').value.trim();
            const active = document.getElementById('editStudentActive').checked;

            if (!name || !hostelBlock || !roomNumber) {
                showToast('Name, block, and room number are required', 'error');
                return;
            }

            try {
                await apiRequest(`/admin/users/${userId}`, {
                    method: 'PUT',
                    body: {
                        name,
                        hostelBlock,
                        roomNumber,
                        active
                    }
                });
                showToast('Student profile updated successfully!', 'success');
                closeEditStudent();
                loadStudentList();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    // Student search input with debounce
    const studentSearchInput = document.getElementById('adminStudentSearchInput');
    if (studentSearchInput) {
        studentSearchInput.oninput = debounce(() => {
            studentState.q = studentSearchInput.value.trim();
            studentState.page = 0;
            loadStudentList();
        }, 300);
    }
}

export async function loadStaffList() {
    const grid = document.getElementById('staffList');
    if (!grid) return;

    grid.innerHTML = '<div class="loading">Loading staff directory...</div>';

    try {
        const data = await apiRequest('/admin/staff');
        const staff = (data && data.staff) || [];
        currentStaff = staff;

        const countEl = document.getElementById('countStaff');
        if (countEl) countEl.textContent = staff.length;

        if (staff.length === 0) {
            grid.innerHTML = '<p class="text-center" style="grid-column: 1 / -1; padding: 24px;">No maintenance staff registered yet.</p>';
            return;
        }

        let html = '';
        staff.forEach(s => {
            const isAvail = s.available !== false;
            const isActive = s.active !== false;
            const availBadge = isAvail
                ? '<span class="status-badge" style="background:#D1FAE5; color:#065F46; font-weight:700;">AVAILABLE</span>'
                : '<span class="status-badge" style="background:#FEE2E2; color:#991B1B; font-weight:700;">BUSY</span>';
            const activeBadge = !isActive
                ? '<span class="badge badge-inactive" style="font-size:0.75rem;">INACTIVE</span>'
                : '';

            html += `
                <div class="staff-card ${!isActive ? 'staff-card-inactive' : ''}">
                    <div class="staff-card-header">
                        <div>
                            <h4 style="margin: 0; font-size: 1.05rem;">${escapeHtml(s.name)}</h4>
                            <div style="font-size: 0.82rem; color: var(--grey); margin-top: 2px;">${escapeHtml(s.email)}</div>
                        </div>
                        <div style="display: flex; gap: 6px; align-items: center;">
                            ${activeBadge}
                            ${availBadge}
                        </div>
                    </div>
                    <div class="staff-card-body" style="margin: 12px 0; font-size: 0.9rem;">
                        <p style="margin: 4px 0;"><strong>Specialization:</strong> <span class="badge" style="background:#E0F2FE; color:#0369A1; font-weight:700;">${escapeHtml(s.specializationDisplay || s.specialization)}</span></p>
                        <p style="margin: 4px 0;"><strong>Active Complaints:</strong> ${s.activeComplaints}</p>
                        <p style="margin: 4px 0;"><strong>Average Rating:</strong> ★ ${s.avgRating || '—'}</p>
                    </div>
                    <div class="staff-card-actions" style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 14px; padding-top: 10px; border-top: 1px dashed var(--border-color);">
                        <button type="button" class="btn btn-outline btn-sm btn-edit-staff" data-user-id="${s.userId}">Edit</button>
                        <button type="button" class="btn btn-outline btn-sm btn-toggle-avail" data-user-id="${s.userId}" data-avail="${isAvail}">
                            ${isAvail ? 'Mark Busy' : 'Mark Available'}
                        </button>
                        <button type="button" class="btn btn-outline btn-sm ${isActive ? 'danger' : ''} btn-toggle-staff-active" data-user-id="${s.userId}" data-active="${isActive}">
                            ${isActive ? 'Deactivate' : 'Activate'}
                        </button>
                    </div>
                </div>
            `;
        });

        grid.innerHTML = html;

        // Wire staff action buttons
        grid.querySelectorAll('.btn-edit-staff').forEach(btn => {
            btn.onclick = () => {
                const uId = parseInt(btn.getAttribute('data-user-id'), 10);
                const s = currentStaff.find(st => st.userId === uId);
                if (s) {
                    const modal = document.getElementById('adminEditStaffModal');
                    if (!modal) return;
                    document.getElementById('editStaffUserId').value = s.userId;
                    document.getElementById('editStaffName').value = s.name || '';
                    document.getElementById('editStaffEmail').value = s.email || '';
                    document.getElementById('editStaffSpecialization').value = s.specialization || 'OTHER';
                    document.getElementById('editStaffAvailable').checked = s.available !== false;
                    document.getElementById('editStaffActive').checked = s.active !== false;
                    modal.style.display = 'flex';
                }
            };
        });

        grid.querySelectorAll('.btn-toggle-avail').forEach(btn => {
            btn.onclick = async () => {
                const uId = parseInt(btn.getAttribute('data-user-id'), 10);
                const isAvail = btn.getAttribute('data-avail') === 'true';
                const s = currentStaff.find(st => st.userId === uId);
                const spec = s ? s.specialization : null;
                try {
                    await apiRequest(`/admin/staff/${uId}`, {
                        method: 'PUT',
                        body: {
                            available: !isAvail,
                            specialization: spec
                        }
                    });
                    showToast(`Staff availability marked as ${!isAvail ? 'Available' : 'Busy'}`, 'success');
                    loadStaffList();
                } catch (err) {
                    showToast(err.message, 'error');
                }
            };
        });

        grid.querySelectorAll('.btn-toggle-staff-active').forEach(btn => {
            btn.onclick = async () => {
                const uId = parseInt(btn.getAttribute('data-user-id'), 10);
                const isActive = btn.getAttribute('data-active') === 'true';
                try {
                    await apiRequest(`/admin/users/${uId}`, {
                        method: 'PUT',
                        body: { active: !isActive }
                    });
                    showToast(`Staff account ${!isActive ? 'activated' : 'deactivated'} successfully`, 'success');
                    loadStaffList();
                } catch (err) {
                    showToast(err.message, 'error');
                }
            };
        });

    } catch (err) {
        grid.innerHTML = `<div class="toast toast-error">${escapeHtml(err.message)}</div>`;
    }
}

export async function loadStudentList() {
    const tbody = document.getElementById('adminStudentsTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="6" class="text-center">Loading students...</td></tr>';

    try {
        const queryParams = new URLSearchParams({
            role: 'STUDENT',
            q: studentState.q,
            page: studentState.page,
            size: studentState.size
        });
        const data = await apiRequest(`/admin/users?${queryParams.toString()}`);
        const students = (data && data.users) || [];
        const total = data ? data.total : 0;
        currentStudents = students;

        const countEl = document.getElementById('countStudents');
        if (countEl) countEl.textContent = total;

        if (students.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center" style="padding: 24px;">No students found matching your search.</td></tr>';
            renderPagination(0, studentState, 'studentPaginationContainer', loadStudentList);
            return;
        }

        let html = '';
        students.forEach(st => {
            const block = escapeHtml(st.hostelBlock || '—');
            const room = escapeHtml(st.roomNumber || '—');
            const isActive = st.active !== false;
            const statusBadge = isActive
                ? '<span class="badge badge-success" style="font-size:0.75rem;">Active</span>'
                : '<span class="badge badge-inactive" style="font-size:0.75rem;">Inactive</span>';

            html += `
                <tr>
                    <td>
                        <div style="display: flex; align-items: center; gap: 8px;">
                            <div class="user-avatar-sm" style="width:28px; height:28px; border-radius:50%; background:#00B4D8; color:#fff; display:flex; align-items:center; justify-content:center; font-weight:700; font-size:0.8rem;">
                                ${(st.name || 'S').charAt(0).toUpperCase()}
                            </div>
                            <strong>${escapeHtml(st.name)}</strong>
                        </div>
                    </td>
                    <td>${escapeHtml(st.email)}</td>
                    <td><span class="meta-tag">Block ${block}</span></td>
                    <td><span class="meta-tag">Room ${room}</span></td>
                    <td>${statusBadge}</td>
                    <td>
                        <div style="display: flex; gap: 6px;">
                            <button type="button" class="btn btn-outline btn-sm btn-edit-student" data-user-id="${st.userId}">Edit</button>
                            <button type="button" class="btn btn-outline btn-sm ${isActive ? 'danger' : ''} btn-toggle-student-active" data-user-id="${st.userId}" data-active="${isActive}">
                                ${isActive ? 'Deactivate' : 'Activate'}
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        tbody.innerHTML = html;

        // Wire edit & deactivate buttons
        tbody.querySelectorAll('.btn-edit-student').forEach(btn => {
            btn.onclick = () => {
                const uId = parseInt(btn.getAttribute('data-user-id'), 10);
                const s = currentStudents.find(st => st.userId === uId);
                if (s) {
                    const modal = document.getElementById('adminEditStudentModal');
                    if (!modal) return;
                    document.getElementById('editStudentUserId').value = s.userId;
                    document.getElementById('editStudentName').value = s.name || '';
                    document.getElementById('editStudentEmail').value = s.email || '';
                    document.getElementById('editStudentBlock').value = s.hostelBlock || '';
                    document.getElementById('editStudentRoom').value = s.roomNumber || '';
                    document.getElementById('editStudentActive').checked = s.active !== false;
                    modal.style.display = 'flex';
                }
            };
        });

        tbody.querySelectorAll('.btn-toggle-student-active').forEach(btn => {
            btn.onclick = async () => {
                const uId = parseInt(btn.getAttribute('data-user-id'), 10);
                const isActive = btn.getAttribute('data-active') === 'true';
                try {
                    await apiRequest(`/admin/users/${uId}`, {
                        method: 'PUT',
                        body: { active: !isActive }
                    });
                    showToast(`Student ${!isActive ? 'activated' : 'deactivated'} successfully`, 'success');
                    loadStudentList();
                } catch (err) {
                    showToast(err.message, 'error');
                }
            };
        });

        renderPagination(total, studentState, 'studentPaginationContainer', loadStudentList);
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center text-red" style="padding: 20px;">Failed to load students: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function debounce(fn, ms) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), ms);
    };
}
