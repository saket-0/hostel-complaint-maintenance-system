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
import { getPriorityInfo, formatStatus } from './app.js';

let allStudentComplaints = [];
let studentActiveFilter = 'OPEN';
let newModalEscHandler = null;
let selectedPhotoFile = null;

function getCategoryIcon(cat) {
    switch ((cat || '').toUpperCase()) {
        case 'ELECTRICAL': return getIcon('zap', 'cat-icon', 14);
        case 'PLUMBING': return getIcon('droplet', 'cat-icon', 14);
        case 'WIFI': return getIcon('wifi', 'cat-icon', 14);
        case 'AC': return getIcon('wind', 'cat-icon', 14) || getIcon('wrench', 'cat-icon', 14);
        case 'CARPENTRY':
        case 'CLEANING':
        default: return getIcon('wrench', 'cat-icon', 14);
    }
}

function getMilestoneGuideText(status) {
    switch (status) {
        case 'NEW':
            return 'Your complaint has been queued. An administrator or automated dispatcher will assign a qualified maintenance technician shortly.';
        case 'ASSIGNED':
            return 'A maintenance technician has been assigned and notified. They are reviewing the ticket and will visit to inspect/repair.';
        case 'IN_PROGRESS':
            return 'Work is actively underway. The maintenance technician is diagnosing and fixing the reported issue.';
        case 'RESOLVED':
            return 'The technician marked this issue as fixed! Please inspect the repair and either confirm resolution with your rating or reopen if unsatisfied.';
        case 'CLOSED':
            return 'This ticket is closed and archived. Thank you for helping keep our campus facilities in top condition!';
        case 'REOPENED':
            return 'You reported that the fix was incomplete. The ticket has been flagged with high urgency for administrative review.';
        default:
            return 'Maintenance staff is handling this ticket according to standard hostel SLA protocols.';
    }
}

function renderCompactProgress(status) {
    let stepText = 'Step 1/4 · Triage';
    let percent = '20%';
    let fillClass = 'status-NEW';

    switch (status) {
        case 'NEW':
            stepText = 'Step 1/4 · New & Triage';
            percent = '20%';
            fillClass = 'status-NEW';
            break;
        case 'ASSIGNED':
            stepText = 'Step 2/4 · Staff Assigned';
            percent = '40%';
            fillClass = 'status-ASSIGNED';
            break;
        case 'IN_PROGRESS':
            stepText = 'Step 3/4 · Work In Progress';
            percent = '65%';
            fillClass = 'status-IN_PROGRESS';
            break;
        case 'RESOLVED':
            stepText = 'Step 4/4 · Maintenance Fixed';
            percent = '85%';
            fillClass = 'status-RESOLVED';
            break;
        case 'CLOSED':
            stepText = 'Closed · Verified';
            percent = '100%';
            fillClass = 'status-CLOSED';
            break;
        case 'REOPENED':
            stepText = 'Reopened · Needs Attention';
            percent = '70%';
            fillClass = 'status-REOPENED';
            break;
        default:
            stepText = formatStatus(status);
            percent = '50%';
    }

    return `
        <div class="student-progress-wrap">
            <div class="student-progress-meta">
                <span>${escapeHtml(stepText)}</span>
                <span>${percent}</span>
            </div>
            <div class="student-progress-bar">
                <div class="student-progress-fill ${fillClass}" style="width: ${percent};"></div>
            </div>
        </div>
    `;
}

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
    selectedPhotoFile = null;

    // Reset location selector to My Room
    const locRoom = document.getElementById('locTypeRoom');
    const locCommon = document.getElementById('locTypeCommon');
    const locRoomLabel = document.getElementById('locTypeRoomLabel');
    const locCommonLabel = document.getElementById('locTypeCommonLabel');
    const commonGroup = document.getElementById('commonAreaGroup');
    const roomMeta = document.getElementById('roomLocationMeta');
    if (locRoom) locRoom.checked = true;
    if (locCommon) locCommon.checked = false;
    if (locRoomLabel) locRoomLabel.classList.add('active');
    if (locCommonLabel) locCommonLabel.classList.remove('active');
    if (commonGroup) commonGroup.style.display = 'none';
    if (roomMeta) roomMeta.style.display = 'inline-flex';

    // Populate confirmed student room
    const user = getCurrentUser();
    const confirmedLocationFull = document.getElementById('confirmedLocationFull');
    const confirmedRoomText = document.getElementById('confirmedRoomText');
    if (user) {
        const blk = user.hostelBlock ? `Block ${user.hostelBlock}` : 'No Block';
        const rm = user.roomNumber ? `Room ${user.roomNumber}` : 'No Room';
        if (confirmedLocationFull) confirmedLocationFull.textContent = `${blk} · ${rm}`;
        if (confirmedRoomText) confirmedRoomText.textContent = `${user.hostelBlock || '?'}-${user.roomNumber || '?'}`;
    }

    // Reset safety risk toggle
    const safetyCard = document.getElementById('safetyRiskCard');
    const safetyBadge = document.getElementById('safetyRiskBadge');
    const safetyCheckbox = document.getElementById('complaintSafetyRisk');
    if (safetyCheckbox) safetyCheckbox.checked = false;
    if (safetyCard) safetyCard.classList.remove('active');
    if (safetyBadge) safetyBadge.style.display = 'none';

    // Reset photo dropzone
    const previewWrap = document.getElementById('photoPreviewWrap');
    const promptWrap = document.getElementById('dropzonePrompt');
    const photoInput = document.getElementById('complaintPhoto');
    if (previewWrap) previewWrap.style.display = 'none';
    if (promptWrap) promptWrap.style.display = 'block';
    if (photoInput) photoInput.value = '';

    // Reset duplicate warning
    const dupWarning = document.getElementById('duplicateComplaintWarning');
    if (dupWarning) dupWarning.style.display = 'none';

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
    const editRoomLink = document.getElementById('editProfileRoomLink');

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

    if (editRoomLink) {
        editRoomLink.onclick = (e) => {
            e.preventDefault();
            const { openProfileModal } = window;
            if (openProfileModal) openProfileModal();
        };
    }

    // Location toggle setup
    const locRoom = document.getElementById('locTypeRoom');
    const locCommon = document.getElementById('locTypeCommon');
    const locRoomLabel = document.getElementById('locTypeRoomLabel');
    const locCommonLabel = document.getElementById('locTypeCommonLabel');
    const commonGroup = document.getElementById('commonAreaGroup');
    const roomMeta = document.getElementById('roomLocationMeta');
    const commonDesc = document.getElementById('commonAreaDesc');

    function syncLocationUI() {
        if (locCommon && locCommon.checked) {
            if (locRoomLabel) locRoomLabel.classList.remove('active');
            if (locCommonLabel) locCommonLabel.classList.add('active');
            if (commonGroup) commonGroup.style.display = 'block';
            if (roomMeta) roomMeta.style.display = 'none';
            if (commonDesc) commonDesc.focus();
        } else {
            if (locRoomLabel) locRoomLabel.classList.add('active');
            if (locCommonLabel) locCommonLabel.classList.remove('active');
            if (commonGroup) commonGroup.style.display = 'none';
            if (roomMeta) roomMeta.style.display = 'inline-flex';
        }
    }

    if (locRoom) locRoom.onchange = syncLocationUI;
    if (locCommon) locCommon.onchange = syncLocationUI;

    // Safety hazard checkbox
    const safetyCheckbox = document.getElementById('complaintSafetyRisk');
    const safetyCard = document.getElementById('safetyRiskCard');
    const safetyBadge = document.getElementById('safetyRiskBadge');
    if (safetyCheckbox) {
        safetyCheckbox.onchange = () => {
            if (safetyCheckbox.checked) {
                if (safetyCard) safetyCard.classList.add('active');
                if (safetyBadge) safetyBadge.style.display = 'inline-block';
            } else {
                if (safetyCard) safetyCard.classList.remove('active');
                if (safetyBadge) safetyBadge.style.display = 'none';
            }
        };
    }

    // Duplicate detection helper
    const categorySelect = document.getElementById('complaintCategory');
    const dupWarning = document.getElementById('duplicateComplaintWarning');

    function checkClientDuplicate() {
        if (!categorySelect || !dupWarning) return;
        const cat = categorySelect.value;
        if (!cat) {
            dupWarning.style.display = 'none';
            return;
        }
        const existing = allStudentComplaints.find(c =>
            c.category === cat && ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED'].includes(c.status)
        );
        if (existing) {
            dupWarning.style.display = 'flex';
            dupWarning.innerHTML = `
                <span>⚠️</span>
                <div>
                    You already have an active <strong>${escapeHtml(existing.categoryDisplay || existing.category)}</strong> complaint 
                    (<a href="#" id="viewDupLink" style="font-weight:700; text-decoration:underline; color:#92400E;">#${escapeHtml(existing.complaintId)}</a>) 
                    filed ${formatRelativeDate(existing.createdDate)}. Duplicate submissions may cause delays.
                </div>
            `;
            const link = document.getElementById('viewDupLink');
            if (link) {
                link.onclick = (e) => {
                    e.preventDefault();
                    closeNewComplaintModal();
                    openComplaintModal(existing.complaintId);
                };
            }
        } else {
            dupWarning.style.display = 'none';
        }
    }

    if (categorySelect) categorySelect.onchange = checkClientDuplicate;

    // Photo drag & drop
    const dropzone = document.getElementById('photoDropzone');
    const photoInput = document.getElementById('complaintPhoto');
    const previewWrap = document.getElementById('photoPreviewWrap');
    const promptWrap = document.getElementById('dropzonePrompt');
    const previewImg = document.getElementById('photoPreviewImg');
    const previewName = document.getElementById('photoPreviewName');
    const previewSize = document.getElementById('photoPreviewSize');
    const removeBtn = document.getElementById('removePhotoBtn');

    function handleFile(file) {
        if (!file) return;
        if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
            showToast('Please select a valid image (JPG, PNG, or WEBP)', 'error');
            return;
        }
        if (file.size > 5 * 1024 * 1024) {
            showToast('Image exceeds 5MB size limit', 'error');
            return;
        }
        selectedPhotoFile = file;
        if (previewName) previewName.textContent = file.name;
        if (previewSize) previewSize.textContent = `${Math.round(file.size / 1024)} KB`;

        const reader = new FileReader();
        reader.onload = (e) => {
            if (previewImg) previewImg.src = e.target.result;
            if (promptWrap) promptWrap.style.display = 'none';
            if (previewWrap) previewWrap.style.display = 'flex';
        };
        reader.readAsDataURL(file);
    }

    if (dropzone) {
        dropzone.onclick = (e) => {
            if (e.target.closest('#removePhotoBtn')) return;
            if (photoInput) photoInput.click();
        };
        dropzone.ondragover = (e) => {
            e.preventDefault();
            dropzone.classList.add('dragover');
        };
        dropzone.ondragleave = () => {
            dropzone.classList.remove('dragover');
        };
        dropzone.ondrop = (e) => {
            e.preventDefault();
            dropzone.classList.remove('dragover');
            if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]) {
                handleFile(e.dataTransfer.files[0]);
            }
        };
    }

    if (photoInput) {
        photoInput.onchange = () => {
            if (photoInput.files && photoInput.files[0]) {
                handleFile(photoInput.files[0]);
            }
        };
    }

    if (removeBtn) {
        removeBtn.onclick = (e) => {
            e.stopPropagation();
            selectedPhotoFile = null;
            if (photoInput) photoInput.value = '';
            if (previewImg) previewImg.src = '';
            if (previewWrap) previewWrap.style.display = 'none';
            if (promptWrap) promptWrap.style.display = 'block';
        };
    }

    const EMERGENCY_KEYWORDS = ['spark', 'sparks', 'sparking', 'flood', 'flooding', 'fire', 'smoke', 'burst', 'short circuit', 'gas leak', 'broken lock'];

    if (descInput && charCount) {
        descInput.oninput = () => {
            const len = descInput.value.length;
            charCount.textContent = `${len} / 2000`;
            charCount.style.color = (len < 10) ? '#D90429' : '#6C757D';

            // Auto-detect safety hazards
            const val = descInput.value.toLowerCase();
            const hasEmergency = EMERGENCY_KEYWORDS.some(kw => val.includes(kw));
            if (hasEmergency && safetyCheckbox && !safetyCheckbox.checked) {
                safetyCheckbox.checked = true;
                if (safetyCard) safetyCard.classList.add('active');
                if (safetyBadge) safetyBadge.style.display = 'inline-block';
                showToast('Urgent keywords detected: Flagged as High Priority Safety Hazard', 'info', 3000);
            }
            checkClientDuplicate();
        };
    }

    if (openNewForm) {
        openNewComplaintModal();
    } else {
        closeNewComplaintModal();
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

            let finalDescription = description;
            if (locCommon && locCommon.checked) {
                const area = (commonDesc?.value || '').trim();
                if (!area) {
                    showToast('Please specify the common area location', 'error');
                    if (commonDesc) commonDesc.focus();
                    return;
                }
                finalDescription = `[Common Area: ${area}]\n\n${finalDescription}`;
            }

            if (safetyCheckbox && safetyCheckbox.checked && !finalDescription.toLowerCase().includes('safety')) {
                finalDescription = `[Safety Hazard] ${finalDescription}`;
            }

            const submitBtn = document.getElementById('submitComplaintBtn');
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.textContent = 'Submitting...';
            }

            try {
                const res = await apiRequest('/complaints', {
                    method: 'POST',
                    body: { category, description: finalDescription }
                });

                // Optional photo upload
                if (selectedPhotoFile) {
                    try {
                        const formData = new FormData();
                        formData.append('photo', selectedPhotoFile);
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

                // Sync counter card active state
                const counterCards = document.querySelectorAll('#studentCountersGrid .student-counter-card');
                counterCards.forEach(cc => {
                    if (cc.dataset.filter === studentActiveFilter) cc.classList.add('active');
                    else cc.classList.remove('active');
                });

                renderFilteredComplaints();
            };
        });
    }

    // Setup 4 clickable KPI counter cards
    const counterCards = document.querySelectorAll('#studentCountersGrid .student-counter-card');
    counterCards.forEach(card => {
        card.onclick = () => {
            const filter = card.dataset.filter;
            if (!filter) return;
            studentActiveFilter = filter;
            counterCards.forEach(c => c.classList.remove('active'));
            card.classList.add('active');

            // Sync with filter chips
            if (chipContainer) {
                const chips = chipContainer.querySelectorAll('.filter-chip');
                chips.forEach(chip => {
                    if (chip.dataset.filter === filter) chip.classList.add('active');
                    else chip.classList.remove('active');
                });
            }

            renderFilteredComplaints();
        };
    });

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

        // Update chips
        const countAllEl = document.getElementById('chipCountAll');
        const countOpenEl = document.getElementById('chipCountOpen');
        const countResolvedEl = document.getElementById('chipCountResolved');
        const countClosedEl = document.getElementById('chipCountClosed');

        if (countAllEl) countAllEl.textContent = allCount;
        if (countOpenEl) countOpenEl.textContent = openCount;
        if (countResolvedEl) countResolvedEl.textContent = resolvedCount;
        if (countClosedEl) countClosedEl.textContent = closedCount;

        // Update KPI Strip
        const kpiOpen = document.getElementById('studentKpiOpen');
        const kpiResolved = document.getElementById('studentKpiResolved');
        const kpiClosed = document.getElementById('studentKpiClosed');
        const kpiRating = document.getElementById('studentKpiRating');
        const kpiRatingSub = document.getElementById('studentKpiRatingSub');

        if (kpiOpen) kpiOpen.textContent = openCount;
        if (kpiResolved) kpiResolved.textContent = resolvedCount;
        if (kpiClosed) kpiClosed.textContent = closedCount;

        // Ratings calculation (format as '★ 5.0 (from N ratings)' or hide when < 3)
        const closedWithRatings = allStudentComplaints.filter(c => {
            const r = c.feedbackRating || (c.feedback && c.feedback.rating);
            return c.status === 'CLOSED' && r !== undefined && r !== null && Number(r) > 0;
        });

        if (closedWithRatings.length >= 3) {
            const sum = closedWithRatings.reduce((acc, c) => acc + Number(c.feedbackRating || (c.feedback && c.feedback.rating)), 0);
            const avg = (sum / closedWithRatings.length).toFixed(1);
            if (kpiRating) kpiRating.textContent = `★ ${avg}`;
            if (kpiRatingSub) kpiRatingSub.textContent = `from ${closedWithRatings.length} ratings`;
        } else {
            if (kpiRating) kpiRating.textContent = '—';
            if (kpiRatingSub) kpiRatingSub.textContent = closedWithRatings.length > 0 ? `${closedWithRatings.length} rating(s) (min 3)` : 'No ratings yet';
        }

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

        const cardPri = getPriorityInfo(c.priority);
        const feedbackVal = c.feedbackRating || (c.feedback && c.feedback.rating);
        const feedbackComment = c.feedbackComment || (c.feedback && c.feedback.comment);

        card.innerHTML = `
            <div class="complaint-card-header">
                <div style="display:flex; align-items:center; gap:8px;">
                    <span class="complaint-id">${escapeHtml(c.complaintId)}</span>
                    <span class="category-badge">${getCategoryIcon(c.category)} ${escapeHtml(c.categoryDisplay || c.category)}</span>
                </div>
                <div class="complaint-meta">
                    <span class="priority-badge ${cardPri.class}">${escapeHtml(cardPri.label)}</span>
                    <span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>
                </div>
            </div>
            ${renderCompactProgress(c.status)}
            <div class="complaint-card-body">
                <p>${escapeHtml(c.description)}</p>
            </div>
            ${c.status === 'RESOLVED' ? `
                <div class="fixed-confirm-banner" onclick="event.stopPropagation();">
                    <div class="fixed-confirm-text">
                        <span>🎉</span> Maintenance marked this fixed! Please confirm or reopen.
                    </div>
                    <div class="fixed-confirm-actions">
                        <button type="button" class="btn btn-teal btn-sm" onclick="window.openComplaintModal('${escapeHtml(c.complaintId)}')">✓ Confirm & Rate</button>
                        <button type="button" class="btn btn-outline btn-sm" onclick="window.promptStudentReopen('${escapeHtml(c.complaintId)}')">✕ Reopen</button>
                    </div>
                </div>
            ` : ''}
            ${c.status === 'CLOSED' && feedbackVal ? `
                <div style="margin: 4px 0 10px 0; font-size: 0.82rem; color: #166534; background: #F0FDF4; border: 1px solid #BBF7D0; padding: 5px 10px; border-radius: 4px; display: inline-flex; align-items: center; gap: 6px;">
                    <span>${'★'.repeat(feedbackVal)}${'☆'.repeat(5 - feedbackVal)}</span>
                    <span style="font-weight: 600;">Rating: ${feedbackVal}/5${feedbackComment ? ` · "${escapeHtml(feedbackComment)}"` : ''}</span>
                </div>
            ` : ''}
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
                    <div class="timeline-title">${escapeHtml(h.oldStatus ? `${formatStatus(h.oldStatus)} ➔ ${formatStatus(h.newStatus)}` : formatStatus(h.newStatus))}</div>
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

    const staffName = c.assignedStaffName || c.staffName;
    const staffSpecialization = c.assignedStaffSpecialization;
    const staffAssigned = Boolean(staffName);

    // Staff Card for Student view
    const staffCardHtml = `
        <div class="assigned-staff-card">
            <div class="assigned-staff-info">
                <div class="assigned-staff-avatar">${staffAssigned ? escapeHtml(staffName.charAt(0).toUpperCase()) : '🔧'}</div>
                <div>
                    <div style="font-weight: 700; font-size: 0.92rem; color: #0B1F4A;">${escapeHtml(staffAssigned ? staffName : 'Maintenance Dispatch Team')}</div>
                    <div style="font-size: 0.78rem; color: #64748B;">${escapeHtml(staffAssigned ? (staffSpecialization ? `${staffSpecialization} Specialist` : 'Assigned Technician') : 'Technician will be assigned shortly')}</div>
                </div>
            </div>
            <span class="badge ${staffAssigned ? 'badge-success' : 'badge-unassigned'}">${staffAssigned ? 'Assigned' : 'Queued'}</span>
        </div>
    `;

    // Expected Resolution Target Card for Student view
    const resolutionTargetHtml = `
        <div class="resolution-target-card">
            <div>
                <div style="font-size: 0.74rem; text-transform: uppercase; font-weight: 700; color: #64748B;">Expected Resolution SLA</div>
                <div style="font-weight: 700; color: #0B1F4A; margin-top: 2px;">${formatExactDate(c.dueBy)} (${formatDueStatus(c.dueBy, c.status)})</div>
            </div>
            <span class="priority-badge ${detailPri.class}">${escapeHtml(detailPri.label)} Priority (${detailPri.key === 'HIGH' ? '24h' : (detailPri.key === 'MEDIUM' ? '72h' : '7d')} SLA)</span>
        </div>
    `;

    // What Happens Next Milestone Guide
    const milestoneGuideHtml = `
        <div class="milestone-guide">
            <div class="milestone-guide-title">
                <span>ℹ️</span> What Happens Next?
            </div>
            <div class="milestone-guide-text">${getMilestoneGuideText(c.status)}</div>
        </div>
    `;

    // Student Info Card (for Admin / Staff view)
    const studentInfoHtml = (!isStudent && hasStudentInfo) ? `
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

    // Fixed? Confirm or Reopen banner for resolved complaints
    const resolvedBannerHtml = (isOwnerStudent && isResolved) ? `
        <div class="fixed-confirm-banner" style="margin: 14px 0 16px 0;">
            <div class="fixed-confirm-text">
                <span>🎉</span> <div><strong>Technician completed work!</strong> Has this issue been fixed satisfactorily?</div>
            </div>
            <div class="fixed-confirm-actions">
                <button type="button" class="btn btn-teal btn-sm" id="btnScrollToRating">✓ Confirm & Rate</button>
                <button type="button" class="btn btn-outline btn-sm" id="btnDrawerReopen">✕ No, Reopen</button>
            </div>
        </div>
    ` : '';

    detailEl.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #0B1F4A; padding-bottom: 10px; margin-bottom: 14px; padding-right: 48px; flex-wrap: wrap; gap: 8px;">
            <div style="display:flex; align-items:center; gap:8px;">
                <h3 style="margin:0; white-space: nowrap;">${escapeHtml(c.complaintId)}</h3>
                <span class="category-badge">${getCategoryIcon(c.category)} ${escapeHtml(c.categoryDisplay || c.category)}</span>
            </div>
            <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
                ${isAdmin && c.status !== 'CLOSED' ? `
                    <div style="display:flex; align-items:center; gap:4px;">
                        <span style="font-size:0.75rem; font-weight:700;">Priority:</span>
                        <select id="detailPrioritySelect" style="font-size:0.8rem; font-weight:700; padding:2px 6px; border:1.5px solid #0B1F4A; border-radius:4px;">
                            <option value="1" ${detailPri.key === 'HIGH' ? 'selected' : ''}>🔴 High</option>
                            <option value="2" ${detailPri.key === 'MEDIUM' ? 'selected' : ''}>🟠 Medium</option>
                            <option value="3" ${detailPri.key === 'LOW' ? 'selected' : ''}>🟢 Low</option>
                        </select>
                    </div>
                ` : `<span class="priority-badge ${detailPri.class}">${escapeHtml(detailPri.label)}</span>`}
                <span class="status-badge status-${c.status}">${escapeHtml(c.statusDisplay || c.status)}</span>
            </div>
        </div>

        ${resolvedBannerHtml}

        ${isStudent ? `
            ${staffCardHtml}
            ${resolutionTargetHtml}
            ${milestoneGuideHtml}
        ` : `
            ${studentInfoHtml}
            <p><strong>Assigned Staff:</strong> ${escapeHtml(staffAssigned ? `${staffName}${staffSpecialization ? ` (${staffSpecialization})` : ''}` : 'None assigned yet')}</p>
            <p><strong>Due Date:</strong> ${formatDate(c.dueBy)} ${c.overdue ? '<span class="overdue-badge">OVERDUE</span>' : ''}</p>
        `}

        <div style="margin: 12px 0;">
            <p style="font-size: 0.95rem; line-height: 1.5; color: #1E293B; background: #F8FAFC; padding: 12px 14px; border: 1px solid #E2E8F0; border-radius: 6px;">
                <strong>Description:</strong><br>${escapeHtml(c.description)}
            </p>
        </div>

        ${c.attachments && c.attachments.length > 0 ? `
            <div style="margin: 12px 0; padding: 10px 14px; background: #F8FAFC; border: 1.5px solid var(--border-color); border-radius: 4px;">
                <div style="font-weight: 700; font-size: 0.85rem; margin-bottom: 6px;">📷 Attachments (${c.attachments.length})</div>
                ${c.attachments.map(att => `<div style="display: flex; align-items: center; gap: 8px; font-size: 0.82rem;"><span>📎</span><span style="font-family: var(--font-mono); font-weight: 600;">${escapeHtml(att.fileName)}</span><span style="color: var(--grey); font-size: 0.75rem;">(${Math.round((att.sizeBytes || 0) / 1024)} KB)</span></div>`).join('')}
            </div>
        ` : ''}

        <div style="margin: 18px 0 14px 0;">
            <div style="font-size: 0.75rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--navy); margin-bottom: 6px;">Lifecycle Stepper</div>
            ${renderProgressStepper(c.status)}
        </div>

        ${actionButtons}
        ${feedbackSection}
        <h4 style="margin-top: 24px;">📜 Audit Timeline</h4>
        ${timelineHtml}
        ${commentsHtml}
    `;

    const scrollToRatingBtn = document.getElementById('btnScrollToRating');
    if (scrollToRatingBtn) {
        scrollToRatingBtn.onclick = () => {
            const formEl = document.getElementById('feedbackForm');
            if (formEl) {
                formEl.scrollIntoView({ behavior: 'smooth' });
                const sel = document.getElementById('feedbackRating');
                if (sel) sel.focus();
            }
        };
    }

    const drawerReopenBtn = document.getElementById('btnDrawerReopen');
    if (drawerReopenBtn) {
        drawerReopenBtn.onclick = () => promptStudentReopen(c.complaintId);
    }

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

export async function promptStudentReopen(complaintId) {
    const reason = prompt('Please specify why this complaint is not fixed or needs rework:');
    if (!reason || !reason.trim()) return;
    try {
        await apiRequest(`/complaints/${complaintId}/reopen`, {
            method: 'POST',
            body: { reason: reason.trim() }
        });
        showToast('Complaint reopened. Staff has been notified.', 'success');
        loadStudentComplaints();
        openComplaintModal(complaintId);
    } catch (err) {
        showToast(err.message, 'error');
    }
}

if (typeof window !== 'undefined') {
    window.promptStudentReopen = promptStudentReopen;
    window.openComplaintModal = openComplaintModal;
}

