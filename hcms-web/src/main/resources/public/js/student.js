/**
 * Student Dashboard Module.
 * Handles complaint registration, viewing student complaints, timeline,
 * feedback submission, comments, closing, and reopening complaints.
 */
import { apiRequest } from './api.js';
import { showToast, escapeHtml } from './toast.js';
import { getCurrentUser } from './auth.js';
import { formatExactDate, formatRelativeDate, formatDueStatus } from './dates.js';
import { getIcon } from './icons.js';

let allStudentComplaints = [];
let studentActiveFilter = 'ALL';
let newModalEscHandler = null;

export function openNewComplaintModal() {
    const modal = document.getElementById('newComplaintModal');
    const formView = document.getElementById('newComplaintFormView');
    const successView = document.getElementById('newComplaintSuccessView');
    const form = document.getElementById('complaintForm');
    const charCount = document.getElementById('charCount');
    const closeBtn = document.getElementById('closeNewComplaintModal');
    const cancelBtn = document.getElementById('cancelComplaint');
    const overlay = document.getElementById('newComplaintOverlay');

    if (!modal) return;

    modal.style.display = 'flex';
    if (formView) formView.style.display = 'block';
    if (successView) successView.style.display = 'none';
    if (form) form.reset();
    if (charCount) {
        charCount.textContent = '0 / 2000';
        charCount.style.color = '#6C757D';
    }

    if (closeBtn) closeBtn.onclick = closeNewComplaintModal;
    if (cancelBtn) cancelBtn.onclick = closeNewComplaintModal;
    if (overlay) overlay.onclick = closeNewComplaintModal;

    if (newModalEscHandler) document.removeEventListener('keydown', newModalEscHandler);
    newModalEscHandler = (e) => {
        if (e.key === 'Escape') closeNewComplaintModal();
    };
    document.addEventListener('keydown', newModalEscHandler);
}

export function closeNewComplaintModal() {
    const modal = document.getElementById('newComplaintModal');
    if (modal) modal.style.display = 'none';
    if (newModalEscHandler) {
        document.removeEventListener('keydown', newModalEscHandler);
        newModalEscHandler = null;
    }
    if (window.location.hash === '#/new') {
        window.location.hash = '#/mine';
    }
}

export function initStudentView(openNewForm = false) {
    const newBtn = document.getElementById('newComplaintBtn');
    const emptySubmitBtn = document.getElementById('studentEmptySubmitBtn');
    const complaintForm = document.getElementById('complaintForm');
    const descInput = document.getElementById('complaintDesc');
    const charCount = document.getElementById('charCount');

    if (newBtn) {
        newBtn.onclick = () => {
            window.location.hash = '#/new';
        };
    }

    if (emptySubmitBtn) {
        emptySubmitBtn.onclick = () => {
            window.location.hash = '#/new';
        };
    }

    if (openNewForm) {
        openNewComplaintModal();
    } else {
        closeNewComplaintModal();
    }

    if (descInput && charCount) {
        descInput.oninput = () => {
            const len = descInput.value.length;
            charCount.textContent = `${len} / 2000`;
            if (len < 10) {
                charCount.style.color = '#D90429';
            } else {
                charCount.style.color = '#6C757D';
            }
        };
    }

    if (complaintForm) {
        complaintForm.onsubmit = async (e) => {
            e.preventDefault();
            const category = document.getElementById('complaintCategory').value;
            const description = descInput.value.trim();

            if (!category) {
                showToast('Please select a category', 'error');
                return;
            }
            if (description.length < 10) {
                showToast('Description must be at least 10 characters', 'error');
                return;
            }

            const submitBtn = document.getElementById('submitComplaintBtn');
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.textContent = 'Submitting...';
            }

            try {
                const res = await apiRequest('/complaints', {
                    method: 'POST',
                    body: { category, description }
                });

                // Optional photo upload
                const photoInput = document.getElementById('complaintPhoto');
                if (photoInput && photoInput.files && photoInput.files[0]) {
                    try {
                        const formData = new FormData();
                        formData.append('photo', photoInput.files[0]);
                        await apiRequest(`/complaints/${res.complaintId}/attachments`, {
                            method: 'POST',
                            body: formData
                        });
                    } catch (attErr) {
                        console.warn('Attachment upload failed:', attErr);
                    }
                }

                showToast(`Complaint registered: ${res.complaintId}`, 'success', 4000);

                // Show Success Screen View
                const formView = document.getElementById('newComplaintFormView');
                const successView = document.getElementById('newComplaintSuccessView');
                const idEl = document.getElementById('successComplaintId');
                const slaEl = document.getElementById('successSlaText');
                const dueEl = document.getElementById('successDueText');

                if (formView) formView.style.display = 'none';
                if (successView) successView.style.display = 'block';
                if (idEl) idEl.textContent = res.complaintId;

                let slaHours = '24 hours';
                const pri = (res.priority || res.priorityLevel || '').toUpperCase();
                if (pri.includes('MEDIUM') || pri.includes('72')) {
                    slaHours = '72 hours';
                } else if (pri.includes('LOW') || pri.includes('168')) {
                    slaHours = '7 days';
                }
                if (slaEl) slaEl.textContent = `Within ${slaHours} (${res.priorityDisplay || res.priority || 'Standard'} SLA)`;
                if (dueEl) dueEl.textContent = res.dueBy ? formatDate(res.dueBy) : 'Estimated based on priority';

                const copyBtn = document.getElementById('copyComplaintIdBtn');
                if (copyBtn) {
                    copyBtn.onclick = () => {
                        if (navigator.clipboard) {
                            navigator.clipboard.writeText(res.complaintId).then(() => {
                                copyBtn.textContent = 'Copied!';
                                setTimeout(() => { copyBtn.textContent = 'Copy'; }, 2000);
                            });
                        }
                    };
                }

                const trackBtn = document.getElementById('trackComplaintBtn');
                if (trackBtn) {
                    trackBtn.onclick = () => {
                        closeNewComplaintModal();
                        loadStudentComplaints();
                        openComplaintModal(res.complaintId);
                    };
                }

                const anotherBtn = document.getElementById('submitAnotherComplaintBtn');
                if (anotherBtn) {
                    anotherBtn.onclick = () => {
                        complaintForm.reset();
                        if (photoInput) photoInput.value = '';
                        if (charCount) {
                            charCount.textContent = '0 / 2000';
                            charCount.style.color = '#6C757D';
                        }
                        if (formView) formView.style.display = 'block';
                        if (successView) successView.style.display = 'none';
                    };
                }

                loadStudentComplaints();
            } catch (err) {
                showToast(err.message, 'error');
            } finally {
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Submit Complaint';
                }
            }
        };
    }

    // Setup filter chips
    const chipContainer = document.getElementById('studentFilterChips');
    if (chipContainer) {
        const chips = chipContainer.querySelectorAll('.filter-chip');
        chips.forEach(chip => {
            chip.onclick = () => {
                chips.forEach(c => c.classList.remove('active'));
                chip.classList.add('active');
                studentActiveFilter = chip.dataset.filter || 'ALL';
                renderFilteredComplaints();
            };
        });
    }

    loadStudentComplaints();
}

export async function loadStudentComplaints() {
    const listEl = document.getElementById('studentComplaints');
    const loadingEl = document.getElementById('studentLoading');
    const emptyEl = document.getElementById('studentEmpty');

    if (!listEl) return;
    if (loadingEl) {
        loadingEl.style.display = 'block';
        loadingEl.innerHTML = `
            <div class="skeleton-card"><div class="skeleton skeleton-line title"></div><div class="skeleton skeleton-line text"></div><div class="skeleton skeleton-line short"></div></div>
            <div class="skeleton-card"><div class="skeleton skeleton-line title"></div><div class="skeleton skeleton-line text"></div><div class="skeleton skeleton-line short"></div></div>
        `;
    }
    if (emptyEl) emptyEl.style.display = 'none';

    try {
        const data = await apiRequest('/complaints/mine');
        allStudentComplaints = (data && data.complaints) || [];

        if (loadingEl) loadingEl.style.display = 'none';

        // Update counts
        const allCount = allStudentComplaints.length;
        const openCount = allStudentComplaints.filter(c => ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED'].includes(c.status)).length;
        const resolvedCount = allStudentComplaints.filter(c => c.status === 'RESOLVED').length;
        const closedCount = allStudentComplaints.filter(c => c.status === 'CLOSED').length;

        const countAllEl = document.getElementById('chipCountAll');
        const countOpenEl = document.getElementById('chipCountOpen');
        const countResolvedEl = document.getElementById('chipCountResolved');
        const countClosedEl = document.getElementById('chipCountClosed');

        if (countAllEl) countAllEl.textContent = allCount;
        if (countOpenEl) countOpenEl.textContent = openCount;
        if (countResolvedEl) countResolvedEl.textContent = resolvedCount;
        if (countClosedEl) countClosedEl.textContent = closedCount;

        renderFilteredComplaints();
    } catch (err) {
        if (loadingEl) loadingEl.style.display = 'none';
        listEl.innerHTML = `
            <div class="inline-error-state">
                <div class="error-icon">⚠️</div>
                <p>Failed to load complaints: ${escapeHtml(err.message)}</p>
                <button type="button" class="btn btn-outline btn-sm" id="retryStudentComplaintsBtn">🔄 Retry</button>
            </div>
        `;
        const retryBtn = document.getElementById('retryStudentComplaintsBtn');
        if (retryBtn) retryBtn.onclick = () => loadStudentComplaints();
        showToast('Failed to load complaints: ' + err.message, 'error');
    }
}

function renderFilteredComplaints() {
    const listEl = document.getElementById('studentComplaints');
    const emptyEl = document.getElementById('studentEmpty');
    if (!listEl) return;

    // Clear existing cards
    const existingCards = listEl.querySelectorAll('.complaint-card');
    existingCards.forEach(c => c.remove());

    let filtered = allStudentComplaints;
    if (studentActiveFilter === 'OPEN') {
        filtered = allStudentComplaints.filter(c => ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED'].includes(c.status));
    } else if (studentActiveFilter === 'RESOLVED') {
        filtered = allStudentComplaints.filter(c => c.status === 'RESOLVED');
    } else if (studentActiveFilter === 'CLOSED') {
        filtered = allStudentComplaints.filter(c => c.status === 'CLOSED');
    }

    if (filtered.length === 0) {
        if (emptyEl) emptyEl.style.display = 'block';
        return;
    }

    if (emptyEl) emptyEl.style.display = 'none';

    filtered.forEach(c => {
        const card = document.createElement('div');
        card.className = 'complaint-card';
        card.setAttribute('tabindex', '0');
        card.setAttribute('role', 'button');
        card.setAttribute('aria-label', `View details for complaint ${c.complaintId}`);
        card.onclick = () => openComplaintModal(c.complaintId);
        card.onkeydown = (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                openComplaintModal(c.complaintId);
            }
        };

        const staffDisplay = (c.assignedStaffName || c.staffName)
            ? `${c.assignedStaffName || c.staffName}${c.assignedStaffSpecialization ? ` (${c.assignedStaffSpecialization})` : ''}`
            : 'Unassigned';

        const blockVal = c.hostelBlock || c.studentHostelBlock || '';
        const roomVal = c.roomNumber || c.studentRoomNumber || '';
        const roomMeta = (blockVal || roomVal)
            ? `<div class="footer-meta-item"><span>📍 Room:</span> ${escapeHtml(blockVal)}${blockVal && roomVal ? ' - ' : ''}${escapeHtml(roomVal)}</div>`
            : '';

        card.innerHTML = `
            <div class="complaint-card-header">
                <div style="display:flex; align-items:center; gap:8px;">
                    <span class="complaint-id">${escapeHtml(c.complaintId)}</span>
                    <span class="category-badge">${escapeHtml(c.categoryDisplay || c.category)}</span>
                </div>
                <div class="complaint-meta">
                    <span class="priority-badge priority-${c.priority}">${escapeHtml(c.priority)}</span>
                    <span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>
                </div>
            </div>
            <div class="complaint-card-stepper" style="margin: 10px 0 12px 0;">
                ${renderProgressStepper(c.status)}
            </div>
            <div class="complaint-card-body">
                <p>${escapeHtml(c.description)}</p>
            </div>
            <div class="complaint-card-footer">
                <div class="footer-meta-item"><span>📅 Reported:</span> ${formatRelativeDate(c.createdDate)}</div>
                <div class="footer-meta-item"><span>⏱ Due By:</span> ${formatDueStatus(c.dueBy, c.status)}</div>
                ${roomMeta}
                <div class="footer-meta-item"><span>👤 Staff:</span> ${escapeHtml(staffDisplay)}</div>
                <div class="card-affordance" style="margin-left: auto;">View details ${getIcon('arrow-right', 'affordance-icon', 13)}</div>
            </div>
        `;
        listEl.appendChild(card);
    });
}

let drawerEscHandler = null;

export function renderProgressStepper(status) {
    const isReopened = status === 'REOPENED';
    const steps = [
        { key: 'NEW', label: 'New' },
        { key: 'ASSIGNED', label: 'Assigned' },
        { key: 'IN_PROGRESS', label: 'In Progress' },
        { key: 'RESOLVED', label: 'Resolved' },
        { key: 'CLOSED', label: 'Closed' }
    ];

    let currentIdx = 0;
    if (status === 'ASSIGNED') currentIdx = 1;
    else if (status === 'IN_PROGRESS') currentIdx = 2;
    else if (status === 'RESOLVED') currentIdx = 3;
    else if (status === 'CLOSED') currentIdx = 4;

    let html = '<div class="progress-stepper" aria-label="Complaint progress">';
    steps.forEach((step, idx) => {
        let stateClass = '';
        let iconHtml = `<span>${idx + 1}</span>`;
        if (isReopened) {
            stateClass = idx <= 3 ? 'completed' : '';
            if (idx <= 3) iconHtml = getIcon('check', 'step-check-icon', 12);
        } else {
            if (idx < currentIdx) {
                stateClass = 'completed';
                iconHtml = getIcon('check', 'step-check-icon', 12);
            } else if (idx === currentIdx) {
                stateClass = 'active';
            }
        }

        html += `
            <div class="step-node ${stateClass}">
                <div class="step-circle">${iconHtml}</div>
                <div class="step-label">${step.label}</div>
            </div>
        `;
    });

    if (isReopened) {
        html += `
            <div class="step-node flagged active">
                <div class="step-circle">${getIcon('alert-triangle', 'step-alert-icon', 14)}</div>
                <div class="step-label">Reopened</div>
            </div>
        `;
    }

    html += '</div>';
    return html;
}

export function closeComplaintDrawer() {
    const modal = document.getElementById('complaintModal');
    if (modal) {
        modal.style.display = 'none';
        modal.classList.remove('open');
    }
    if (drawerEscHandler) {
        document.removeEventListener('keydown', drawerEscHandler);
        drawerEscHandler = null;
    }
}

export async function openComplaintModal(complaintId) {
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

    if (drawerEscHandler) document.removeEventListener('keydown', drawerEscHandler);
    drawerEscHandler = (e) => {
        if (e.key === 'Escape') {
            closeComplaintDrawer();
        } else if (e.key === 'Tab') {
            const drawerContent = document.getElementById('drawerContent');
            if (drawerContent) {
                const focusables = drawerContent.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
                if (focusables.length > 0) {
                    const first = focusables[0];
                    const last = focusables[focusables.length - 1];
                    if (e.shiftKey && document.activeElement === first) {
                        e.preventDefault();
                        last.focus();
                    } else if (!e.shiftKey && document.activeElement === last) {
                        e.preventDefault();
                        first.focus();
                    }
                }
            }
        }
    };
    document.addEventListener('keydown', drawerEscHandler);
    if (closeBtn) setTimeout(() => closeBtn.focus(), 50);

    try {
        const [complaint, historyRes, commentsRes] = await Promise.all([
            apiRequest(`/complaints/${complaintId}`),
            apiRequest(`/complaints/${complaintId}/history`),
            apiRequest(`/complaints/${complaintId}/comments`)
        ]);

        const historyList = (historyRes && Array.isArray(historyRes.history)) 
            ? historyRes.history 
            : (Array.isArray(historyRes) ? historyRes : (complaint && Array.isArray(complaint.history) ? complaint.history : []));

        const commentsList = (commentsRes && Array.isArray(commentsRes.comments)) 
            ? commentsRes.comments 
            : (Array.isArray(commentsRes) ? commentsRes : (complaint && Array.isArray(complaint.comments) ? complaint.comments : []));

        renderComplaintDetail(complaint, historyList, commentsList);
    } catch (err) {
        detailEl.innerHTML = `<div class="toast toast-error">${escapeHtml(err.message)}</div>`;
    }
}

function renderComplaintDetail(c, history, comments) {
    const detailEl = document.getElementById('complaintDetail');
    if (!detailEl) return;

    const currentUser = getCurrentUser();
    const isStudent = currentUser && currentUser.role === 'STUDENT';
    const isOwnerStudent = isStudent && (String(currentUser.userId) === String(c.studentId));
    const isAdmin = currentUser && currentUser.role === 'ADMIN';
    const isStaff = currentUser && currentUser.role === 'STAFF';

    const isResolved = c.status === 'RESOLVED';
    const isClosed = c.status === 'CLOSED';
    const hasFeedback = (c.feedbackRating !== undefined && c.feedbackRating !== null) || (c.feedback && c.feedback.rating !== undefined);
    const feedbackRating = c.feedbackRating || (c.feedback && c.feedback.rating);
    const feedbackComment = c.feedbackComment || (c.feedback && c.feedback.comment);

    const allowed = c.allowedActions || [];

    // Action buttons: driven by server allowedActions AND role guards
    let actionButtons = '';
    const actionList = [];
    if (allowed.includes('CLOSE') && (isOwnerStudent || isAdmin)) {
        actionList.push('<button class="btn btn-teal btn-sm" id="btnActionClose">Close Complaint</button>');
    }
    if (allowed.includes('REOPEN') && (isOwnerStudent || isAdmin)) {
        actionList.push('<button class="btn btn-amber btn-sm" id="btnActionReopen">Reopen Complaint</button>');
    }
    if (allowed.includes('ACCEPT') && isStaff) {
        actionList.push(`<button class="btn btn-primary btn-sm" id="btnActionAccept" data-id="${c.complaintId}">Accept Task</button>`);
    }
    if (allowed.includes('RESOLVE') && isStaff) {
        actionList.push(`<button class="btn btn-teal btn-sm" id="btnActionResolve" data-id="${c.complaintId}">Mark as Resolved</button>`);
    }
    if (allowed.includes('ASSIGN') && isAdmin) {
        actionList.push(`<button class="btn btn-primary btn-sm" id="btnActionAssign" data-id="${c.complaintId}" data-cat="${c.category}">Assign Staff</button>`);
    }
    if (allowed.includes('REASSIGN') && isAdmin) {
        actionList.push(`<button class="btn btn-outline btn-sm" id="btnActionReassign" data-id="${c.complaintId}" data-cat="${c.category}">Reassign Staff</button>`);
    }
    if (actionList.length > 0) {
        actionButtons = `<div class="form-actions" style="margin-top: 15px; display: flex; gap: 10px; flex-wrap: wrap;">${actionList.join('')}</div>`;
    }

    // Feedback section:
    // If feedback exists: EVERYONE (Student, Staff, Admin) can view it
    // If GIVE_FEEDBACK in allowedActions AND user is the owner student: render interactive feedback form
    // Staff & Admin see a polite waiting note when awaiting review
    let feedbackSection = '';
    if (hasFeedback) {
        feedbackSection = `
            <div class="card" style="margin-top: 15px; background: #fdfbf7; border: 2px solid var(--border-color); box-shadow: 2px 2px 0 var(--border-color);">
                <h4>Student Feedback</h4>
                <p><strong>Rating:</strong> ${'★'.repeat(feedbackRating)}${'☆'.repeat(5 - feedbackRating)} (${feedbackRating}/5)</p>
                <p><strong>Comment:</strong> ${escapeHtml(feedbackComment || 'No comment provided')}</p>
            </div>
        `;
    } else if (isOwnerStudent && isResolved && allowed.includes('GIVE_FEEDBACK')) {
        feedbackSection = `
            <div class="card" style="margin-top: 15px; background: #eef9fc; border: 2px solid var(--border-color); box-shadow: 2px 2px 0 var(--border-color);">
                <h4>Provide Feedback</h4>
                <form id="feedbackForm">
                    <div class="form-group">
                        <label for="feedbackRating">Rating (1 to 5 Stars)</label>
                        <select id="feedbackRating" required>
                            <option value="5">★★★★★ - Excellent (5)</option>
                            <option value="4">★★★★☆ - Good (4)</option>
                            <option value="3">★★★☆☆ - Average (3)</option>
                            <option value="2">★★☆☆☆ - Poor (2)</option>
                            <option value="1">★☆☆☆☆ - Very Poor (1)</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="feedbackComment">Comment</label>
                        <textarea id="feedbackComment" rows="2" placeholder="Tell us how the service was..."></textarea>
                    </div>
                    <button type="submit" class="btn btn-teal btn-sm">Submit Feedback</button>
                </form>
            </div>
        `;
    } else if (isResolved && (isStaff || isAdmin || (!isOwnerStudent && isStudent))) {
        feedbackSection = `
            <div style="margin-top: 14px; padding: 10px 14px; background: #F8FAFC; border: 1.5px dashed var(--border-color); font-size: 0.85rem; color: var(--grey);">
                <em>Awaiting student review and feedback.</em>
            </div>
        `;
    }

    // Timeline items
    let timelineHtml = '<div class="timeline">';
    if (history && history.length > 0) {
        history.forEach(h => {
            const author = h.changedByName ? ` • ${escapeHtml(h.changedByName)}` : '';
            timelineHtml += `
                <div class="timeline-item">
                    <div class="timeline-time">${formatDate(h.changedAt)}${author}</div>
                    <div class="timeline-title">${escapeHtml(h.oldStatus ? `${h.oldStatus} ➔ ${h.newStatus}` : h.newStatus)}</div>
                    ${h.note ? `<div class="timeline-note">📝 ${escapeHtml(h.note)}</div>` : ''}
                </div>
            `;
        });
    } else {
        timelineHtml += '<p class="text-muted">No timeline entries recorded.</p>';
    }
    timelineHtml += '</div>';

    // Comments thread
    let commentsHtml = '<div class="comments-section"><h4>💬 Discussion</h4><div id="commentsList">';
    if (comments && comments.length > 0) {
        comments.forEach(cm => {
            commentsHtml += `
                <div class="comment-item">
                    <div class="comment-header">
                        <span class="comment-author">${escapeHtml(cm.authorName || 'User')} (${escapeHtml(cm.authorRole || 'STUDENT')})</span>
                        <span>${formatDate(cm.createdAt)}</span>
                    </div>
                    <p>${escapeHtml(cm.message)}</p>
                </div>
            `;
        });
    } else {
        commentsHtml += '<p style="color: #666; font-size: 0.85rem; margin-bottom: 10px;">No comments yet.</p>';
    }
    commentsHtml += `</div>
        <form id="addCommentForm" style="display:flex; gap: 8px; margin-top: 12px;">
            <input type="text" id="newCommentInput" placeholder="Add a comment..." required style="flex:1; padding: 8px 12px; border: 2px solid #0B1F4A;">
            <button type="submit" class="btn btn-primary btn-sm">Send</button>
        </form>
    </div>`;

    const studentNameVal = c.studentName;
    const studentEmailVal = c.studentEmail;
    const studentBlockVal = c.studentHostelBlock || c.hostelBlock;
    const studentRoomVal = c.studentRoomNumber || c.roomNumber;
    const hasStudentInfo = Boolean(studentNameVal || studentEmailVal || studentBlockVal || studentRoomVal);

    const studentInfoHtml = hasStudentInfo ? `
        <div class="student-detail-card">
            <div class="student-detail-header">
                <span class="student-detail-title">
                    <span>👤</span> Student Information
                </span>
                <span class="student-badge">Student</span>
            </div>
            <div class="student-detail-grid">
                <div class="student-detail-item">
                    <span class="student-detail-label">Name</span>
                    <span class="student-detail-value" title="${escapeHtml(studentNameVal || 'Unknown')}">
                        ${escapeHtml(studentNameVal || 'Not specified')}
                    </span>
                </div>
                <div class="student-detail-item">
                    <span class="student-detail-label">Hostel & Room</span>
                    <span class="student-detail-value">
                        <span class="meta-tag">
                            🏢 ${escapeHtml(studentBlockVal || 'Block ?')} - ${escapeHtml(studentRoomVal || 'Room ?')}
                        </span>
                    </span>
                </div>
                <div class="student-detail-item" style="grid-column: 1 / -1;">
                    <span class="student-detail-label">Email</span>
                    <span class="student-detail-value" style="font-family: var(--font-mono); font-size: 0.82rem;">
                        ✉️ ${studentEmailVal ? `<a href="mailto:${escapeHtml(studentEmailVal)}" style="color:var(--navy); text-decoration:underline;">${escapeHtml(studentEmailVal)}</a>` : '<span style="color:var(--grey);">Not available</span>'}
                    </span>
                </div>
            </div>
        </div>
    ` : '';

    detailEl.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #0B1F4A; padding-bottom: 10px; margin-bottom: 14px; padding-right: 48px; flex-wrap: wrap; gap: 8px;">
            <h3 style="margin:0; white-space: nowrap;">${escapeHtml(c.complaintId)}</h3>
            <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
                ${isAdmin && c.status !== 'CLOSED' ? `
                    <div style="display:flex; align-items:center; gap:4px;">
                        <span style="font-size:0.75rem; font-weight:700;">Priority:</span>
                        <select id="detailPrioritySelect" style="font-size:0.8rem; font-weight:700; padding:2px 6px; border:1.5px solid #0B1F4A; border-radius:4px;">
                            <option value="1" ${c.priority === 'HIGH' ? 'selected' : ''}>🔴 High</option>
                            <option value="2" ${c.priority === 'MEDIUM' ? 'selected' : ''}>🟠 Medium</option>
                            <option value="3" ${c.priority === 'LOW' ? 'selected' : ''}>🟢 Low</option>
                        </select>
                    </div>
                ` : `<span class="priority-badge priority-${c.priority}">${escapeHtml(c.priority)}</span>`}
                <span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>
            </div>
        </div>
        ${studentInfoHtml}
        <p><strong>Category:</strong> ${escapeHtml(c.categoryDisplay || c.category)}</p>
        <p><strong>Description:</strong> ${escapeHtml(c.description)}</p>
        <p><strong>Assigned Staff:</strong> ${escapeHtml((c.assignedStaffName || c.staffName) ? `${c.assignedStaffName || c.staffName}${c.assignedStaffSpecialization ? ` (${c.assignedStaffSpecialization})` : ''}` : 'None assigned yet')}</p>
        <p><strong>Due Date:</strong> ${formatDate(c.dueBy)} ${c.overdue ? '<span class="overdue-badge">OVERDUE</span>' : ''}</p>
        ${c.attachments && c.attachments.length > 0 ? `
            <div style="margin: 12px 0; padding: 10px 14px; background: #F8FAFC; border: 1.5px solid var(--border-color); border-radius: 4px;">
                <div style="font-weight: 700; font-size: 0.85rem; margin-bottom: 6px;">📷 Attachments (${c.attachments.length})</div>
                ${c.attachments.map(att => `<div style="display: flex; align-items: center; gap: 8px; font-size: 0.82rem;"><span>📎</span><span style="font-family: var(--font-mono); font-weight: 600;">${escapeHtml(att.fileName)}</span><span style="color: var(--grey); font-size: 0.75rem;">(${Math.round((att.sizeBytes || 0) / 1024)} KB)</span></div>`).join('')}
            </div>
        ` : ''}
        <div style="margin: 18px 0 14px 0;">
            <div style="font-size: 0.75rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--navy); margin-bottom: 6px;">Lifecycle Progress</div>
            ${renderProgressStepper(c.status)}
        </div>
        ${actionButtons}
        ${feedbackSection}
        <h4 style="margin-top: 24px;">📜 Audit Timeline</h4>
        ${timelineHtml}
        ${commentsHtml}
    `;

    // Bind action buttons
    const prioritySelect = document.getElementById('detailPrioritySelect');
    if (prioritySelect) {
        prioritySelect.onchange = async () => {
            const newPri = parseInt(prioritySelect.value, 10);
            try {
                await apiRequest(`/admin/complaints/${c.complaintId}/priority`, {
                    method: 'PUT',
                    body: { priority: newPri }
                });
                showToast(`Priority updated for ${c.complaintId}`, 'success');
                openComplaintModal(c.complaintId);
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    const closeBtn = document.getElementById('btnActionClose');
    if (closeBtn) {
        closeBtn.onclick = async () => {
            if (!confirm('Are you sure you want to close this complaint?')) return;
            try {
                await apiRequest(`/complaints/${c.complaintId}/close`, { method: 'POST' });
                showToast('Complaint closed', 'success');
                openComplaintModal(c.complaintId);
                if (typeof loadStudentComplaints === 'function') loadStudentComplaints();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    const reopenBtn = document.getElementById('btnActionReopen');
    if (reopenBtn) {
        reopenBtn.onclick = async () => {
            const reason = prompt('Please enter reason for reopening:');
            if (!reason) return;
            try {
                await apiRequest(`/complaints/${c.complaintId}/reopen`, {
                    method: 'POST',
                    body: { reason }
                });
                showToast('Complaint reopened', 'success');
                openComplaintModal(c.complaintId);
                if (typeof loadStudentComplaints === 'function') loadStudentComplaints();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    const acceptBtn = document.getElementById('btnActionAccept');
    if (acceptBtn) {
        acceptBtn.onclick = async () => {
            try {
                await apiRequest(`/staff/tasks/${c.complaintId}/accept`, { method: 'POST' });
                showToast('Task accepted', 'success');
                openComplaintModal(c.complaintId);
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    const resolveBtn = document.getElementById('btnActionResolve');
    if (resolveBtn) {
        resolveBtn.onclick = async () => {
            const note = prompt('Optional resolution note:');
            try {
                await apiRequest(`/staff/tasks/${c.complaintId}/status`, {
                    method: 'PUT',
                    body: { status: 'RESOLVED', note: note || 'Resolved by staff' }
                });
                showToast('Task marked as resolved', 'success');
                openComplaintModal(c.complaintId);
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    const assignBtn = document.getElementById('btnActionAssign');
    if (assignBtn) {
        assignBtn.onclick = () => {
            closeComplaintModal();
            const { openAssignModal } = window;
            if (openAssignModal) openAssignModal(c.complaintId, c.category);
        };
    }

    const reassignBtn = document.getElementById('btnActionReassign');
    if (reassignBtn) {
        reassignBtn.onclick = () => {
            closeComplaintModal();
            const { openAssignModal } = window;
            if (openAssignModal) openAssignModal(c.complaintId, c.category);
        };
    }

    // Bind feedback form
    const feedbackForm = document.getElementById('feedbackForm');
    if (feedbackForm) {
        feedbackForm.onsubmit = async (e) => {
            e.preventDefault();
            const rating = parseInt(document.getElementById('feedbackRating').value, 10);
            const comment = document.getElementById('feedbackComment').value.trim();

            try {
                await apiRequest(`/complaints/${c.complaintId}/feedback`, {
                    method: 'POST',
                    body: { rating, comment }
                });
                showToast('Thank you for your feedback!', 'success');
                openComplaintModal(c.complaintId);
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    // Bind comment form
    const commentForm = document.getElementById('addCommentForm');
    if (commentForm) {
        commentForm.onsubmit = async (e) => {
            e.preventDefault();
            const input = document.getElementById('newCommentInput');
            const message = input.value.trim();
            if (!message) return;

            try {
                await apiRequest(`/complaints/${c.complaintId}/comments`, {
                    method: 'POST',
                    body: { message }
                });
                input.value = '';
                // Reload comments
                const updatedComments = await apiRequest(`/complaints/${c.complaintId}/comments`);
                const commentsList = document.getElementById('commentsList');
                if (commentsList) {
                    const cItems = (updatedComments && Array.isArray(updatedComments.comments))
                        ? updatedComments.comments
                        : (Array.isArray(updatedComments) ? updatedComments : []);
                    let cHtml = '';
                    cItems.forEach(cm => {
                        cHtml += `
                            <div class="comment-item">
                                <div class="comment-header">
                                    <span class="comment-author">${escapeHtml(cm.authorName || 'User')} (${escapeHtml(cm.authorRole || 'STUDENT')})</span>
                                    <span>${formatDate(cm.createdAt)}</span>
                                </div>
                                <p>${escapeHtml(cm.message)}</p>
                            </div>
                        `;
                    });
                    commentsList.innerHTML = cHtml || '<p style="color: #666; font-size: 0.85rem; margin-bottom: 10px;">No comments yet.</p>';
                }
                showToast('Comment added', 'info');
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }
}

export function formatDate(dateStr) {
    if (!dateStr) return '—';
    try {
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return dateStr;
        return d.toLocaleDateString('en-IN', {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (e) {
        return dateStr;
    }
}
