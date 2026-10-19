/**
 * Student Dashboard Module.
 * Handles complaint registration, viewing student complaints, timeline,
 * feedback submission, comments, closing, and reopening complaints.
 */
import { apiRequest } from './api.js';
import { showToast, escapeHtml } from './toast.js';

export function initStudentView() {
    const newBtn = document.getElementById('newComplaintBtn');
    const formCard = document.getElementById('newComplaintForm');
    const cancelBtn = document.getElementById('cancelComplaint');
    const complaintForm = document.getElementById('complaintForm');
    const descInput = document.getElementById('complaintDesc');
    const charCount = document.getElementById('charCount');

    if (newBtn && formCard) {
        newBtn.onclick = () => {
            formCard.style.display = formCard.style.display === 'none' ? 'block' : 'none';
        };
    }

    if (cancelBtn && formCard) {
        cancelBtn.onclick = () => {
            formCard.style.display = 'none';
            complaintForm.reset();
            charCount.textContent = '0 / 2000';
        };
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

            try {
                const res = await apiRequest('/complaints', {
                    method: 'POST',
                    body: { category, description }
                });

                showToast(`Complaint registered: ${res.complaintId}`, 'success', 5000);
                formCard.style.display = 'none';
                complaintForm.reset();
                charCount.textContent = '0 / 2000';
                loadStudentComplaints();
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }

    loadStudentComplaints();
}

export async function loadStudentComplaints() {
    const listEl = document.getElementById('studentComplaints');
    const loadingEl = document.getElementById('studentLoading');
    const emptyEl = document.getElementById('studentEmpty');

    if (!listEl) return;
    if (loadingEl) loadingEl.style.display = 'block';
    if (emptyEl) emptyEl.style.display = 'none';

    try {
        const data = await apiRequest('/complaints/mine');
        const complaints = (data && data.complaints) || [];

        // Clear existing cards
        const existingCards = listEl.querySelectorAll('.complaint-card');
        existingCards.forEach(c => c.remove());

        if (loadingEl) loadingEl.style.display = 'none';

        if (complaints.length === 0) {
            if (emptyEl) emptyEl.style.display = 'block';
            return;
        }

        complaints.forEach(c => {
            const card = document.createElement('div');
            card.className = 'complaint-card';
            card.onclick = () => openComplaintModal(c.complaintId);

            const isOverdue = c.overdue ? `<span class="overdue-badge">OVERDUE</span>` : '';

            card.innerHTML = `
                <div class="complaint-card-header">
                    <span class="complaint-id">${escapeHtml(c.complaintId)}</span>
                    <div class="complaint-meta">
                        <span class="priority-badge priority-${c.priority}">${escapeHtml(c.priority)}</span>
                        <span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>
                        ${isOverdue}
                    </div>
                </div>
                <div class="complaint-card-body">
                    <p><strong>${escapeHtml(c.categoryDisplay || c.category)}:</strong> ${escapeHtml(c.description)}</p>
                </div>
                <div class="complaint-card-footer">
                    <span>📅 Reported: ${formatDate(c.createdDate)}</span>
                    <span>⏱ Due By: ${formatDate(c.dueBy)}</span>
                    <span>👤 Staff: ${escapeHtml(c.staffName || 'Unassigned')}</span>
                </div>
            `;
            listEl.appendChild(card);
        });
    } catch (err) {
        if (loadingEl) loadingEl.style.display = 'none';
        showToast('Failed to load complaints: ' + err.message, 'error');
    }
}

export async function openComplaintModal(complaintId) {
    const modal = document.getElementById('complaintModal');
    const detailEl = document.getElementById('complaintDetail');
    const closeBtn = document.getElementById('closeModal');

    if (!modal || !detailEl) return;

    modal.style.display = 'flex';
    detailEl.innerHTML = '<div class="loading">Loading complaint details...</div>';

    closeBtn.onclick = () => { modal.style.display = 'none'; };
    modal.querySelector('.modal-overlay').onclick = () => { modal.style.display = 'none'; };

    try {
        const [complaint, history, comments] = await Promise.all([
            apiRequest(`/complaints/${complaintId}`),
            apiRequest(`/complaints/${complaintId}/history`),
            apiRequest(`/complaints/${complaintId}/comments`)
        ]);

        renderComplaintDetail(complaint, history, comments);
    } catch (err) {
        detailEl.innerHTML = `<div class="toast toast-error">${escapeHtml(err.message)}</div>`;
    }
}

function renderComplaintDetail(c, history, comments) {
    const detailEl = document.getElementById('complaintDetail');
    if (!detailEl) return;

    const isResolved = c.status === 'RESOLVED';
    const isClosed = c.status === 'CLOSED';
    const hasFeedback = c.feedbackRating !== undefined && c.feedbackRating !== null;

    let actionButtons = '';
    if (isResolved) {
        actionButtons = `
            <div class="form-actions" style="margin-top: 15px;">
                <button class="btn btn-teal btn-sm" id="btnActionClose">Close Complaint</button>
                <button class="btn btn-amber btn-sm" id="btnActionReopen">Reopen Complaint</button>
            </div>
        `;
    }

    let feedbackSection = '';
    if (hasFeedback) {
        feedbackSection = `
            <div class="card" style="margin-top: 15px; background: #fdfbf7;">
                <h4>⭐ Student Feedback</h4>
                <p><strong>Rating:</strong> ${'★'.repeat(c.feedbackRating)}${'☆'.repeat(5 - c.feedbackRating)} (${c.feedbackRating}/5)</p>
                <p><strong>Comment:</strong> ${escapeHtml(c.feedbackComment || 'No comment provided')}</p>
            </div>
        `;
    } else if (isResolved || isClosed) {
        feedbackSection = `
            <div class="card" style="margin-top: 15px; background: #eef9fc;">
                <h4>⭐ Provide Feedback</h4>
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
    }

    // Timeline items
    let timelineHtml = '<div class="timeline">';
    if (history && history.length > 0) {
        history.forEach(h => {
            timelineHtml += `
                <div class="timeline-item">
                    <div class="timeline-time">${formatDate(h.changedAt)}</div>
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

    detailEl.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #0B1F4A; padding-bottom: 10px; margin-bottom: 14px;">
            <h3 style="margin:0;">${escapeHtml(c.complaintId)}</h3>
            <div>
                <span class="priority-badge priority-${c.priority}">${escapeHtml(c.priority)}</span>
                <span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>
            </div>
        </div>
        <p><strong>Category:</strong> ${escapeHtml(c.categoryDisplay || c.category)}</p>
        <p><strong>Description:</strong> ${escapeHtml(c.description)}</p>
        <p><strong>Assigned Staff:</strong> ${escapeHtml(c.staffName || 'None assigned yet')}</p>
        <p><strong>Due Date:</strong> ${formatDate(c.dueBy)} ${c.overdue ? '<span class="overdue-badge">OVERDUE</span>' : ''}</p>
        ${actionButtons}
        ${feedbackSection}
        <h4 style="margin-top: 20px;">📜 Audit Timeline</h4>
        ${timelineHtml}
        ${commentsHtml}
    `;

    // Bind action buttons
    const closeBtn = document.getElementById('btnActionClose');
    if (closeBtn) {
        closeBtn.onclick = async () => {
            if (!confirm('Are you sure you want to close this complaint?')) return;
            try {
                await apiRequest(`/complaints/${c.complaintId}/close`, { method: 'POST' });
                showToast('Complaint closed', 'success');
                openComplaintModal(c.complaintId);
                loadStudentComplaints();
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
                loadStudentComplaints();
            } catch (err) {
                showToast(err.message, 'error');
            }
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
                    let cHtml = '';
                    updatedComments.forEach(cm => {
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
                    commentsList.innerHTML = cHtml;
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
