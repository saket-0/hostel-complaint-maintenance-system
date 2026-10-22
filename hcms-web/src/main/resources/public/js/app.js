/**
 * HCMS Single Page Application Root.
 * Manages single-level role navigation, hash routing, view switching, and lifecycle initialization.
 */
import { checkAuth, login, register, logout, getCurrentUser, setCurrentUser } from './auth.js';
export { getCurrentUser } from './auth.js';
import { apiRequest } from './api.js';
import { showToast } from './toast.js';
import { initStudentView } from './student.js';
import { initAdminView } from './admin.js';
import { initStaffView } from './staff.js';
import { initHistoryView } from './history.js';
import { initNotifications, stopNotificationPolling } from './notifications.js';
import { initMetricsFooter } from './metrics.js';
import { getIcon } from './icons.js';

const ROLE_ROUTES = {
    STUDENT: ['#/mine', '#/new', '#/history'],
    STAFF: ['#/tasks', '#/completed', '#/history'],
    ADMIN: ['#/triage', '#/complaints', '#/analytics', '#/staff', '#/history']
};

const DEFAULT_ROUTE = {
    STUDENT: '#/mine',
    STAFF: '#/tasks',
    ADMIN: '#/triage'
};

document.addEventListener('DOMContentLoaded', async () => {
    initMetricsFooter();
    checkDevEnvironment();
    setupAuthFormToggles();
    setupAuthFormSubmissions();
    setupProfileMenu();
    setupProfileModal();
    setupLogout();

    window.addEventListener('hashchange', handleRoute);

    // Initial session check
    const user = await checkAuth();
    if (user) {
        if (user.forcePasswordChange) {
            promptForcePasswordChange(user, '');
        }
        const currentHash = window.location.hash;
        if (!currentHash || currentHash === '#/login' || currentHash === '#/register' || currentHash === '#/dashboard') {
            window.location.hash = DEFAULT_ROUTE[user.role] || '#/login';
        } else {
            handleRoute();
        }
    } else {
        window.location.hash = '#/login';
        handleRoute();
    }
});

async function checkDevEnvironment() {
    try {
        const res = await fetch('/api/v1/metrics-lite');
        if (res.ok) {
            const data = await res.json();
            const container = document.getElementById('quickLoginContainer');
            if (container) {
                if (data.appEnv && data.appEnv !== 'dev') {
                    container.style.display = 'none';
                } else {
                    container.style.display = 'block';
                }
            }
        }
    } catch (e) {
        // Fallback keep default in dev
    }
}

function setupAuthFormToggles() {
    const showRegister = document.getElementById('showRegister');
    const showLogin = document.getElementById('showLogin');
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');

    if (showRegister && showLogin) {
        showRegister.onclick = (e) => {
            e.preventDefault();
            window.location.hash = '#/register';
        };

        showLogin.onclick = (e) => {
            e.preventDefault();
            window.location.hash = '#/login';
        };
    }
}

function promptForcePasswordChange(user, prefilledPassword) {
    const modal = document.getElementById('forcePasswordChangeModal');
    if (!modal) return;
    const errEl = document.getElementById('forcePasswordError');
    if (errEl) errEl.style.display = 'none';

    const currInput = document.getElementById('forceCurrentPassword');
    const newInput = document.getElementById('forceNewPassword');
    const confirmInput = document.getElementById('forceConfirmPassword');
    const form = document.getElementById('forcePasswordChangeForm');

    if (currInput) currInput.value = prefilledPassword || '';
    if (newInput) newInput.value = '';
    if (confirmInput) confirmInput.value = '';

    modal.style.display = 'flex';

    if (form) {
        form.onsubmit = async (e) => {
            e.preventDefault();
            if (errEl) errEl.style.display = 'none';

            const currentPassword = currInput ? currInput.value : '';
            const newPassword = newInput ? newInput.value : '';
            const confirmPassword = confirmInput ? confirmInput.value : '';

            if (!currentPassword) {
                if (errEl) {
                    errEl.textContent = 'Please enter your current temporary password.';
                    errEl.style.display = 'block';
                }
                return;
            }

            if (!newPassword || newPassword.length < 6) {
                if (errEl) {
                    errEl.textContent = 'New password must be at least 6 characters.';
                    errEl.style.display = 'block';
                }
                return;
            }

            if (newPassword !== confirmPassword) {
                if (errEl) {
                    errEl.textContent = 'Passwords do not match.';
                    errEl.style.display = 'block';
                }
                return;
            }

            try {
                await apiRequest('/auth/change-password', {
                    method: 'POST',
                    body: { currentPassword, newPassword }
                });
                modal.style.display = 'none';
                user.forcePasswordChange = false;
                showToast('Password changed successfully! Welcome to HCMS.', 'success');
                const targetRoute = DEFAULT_ROUTE[user.role] || '#/login';
                if (window.location.hash === targetRoute) {
                    await handleRoute();
                } else {
                    window.location.hash = targetRoute;
                }
            } catch (err) {
                if (errEl) {
                    errEl.textContent = err.message || 'Failed to update password.';
                    errEl.style.display = 'block';
                } else {
                    showToast(err.message, 'error');
                }
            }
        };
    }
}

async function executeLogin(email, password) {
    try {
        const user = await login(email, password);
        if (user && user.forcePasswordChange) {
            promptForcePasswordChange(user, password);
            return;
        }
        const targetRoute = DEFAULT_ROUTE[user.role] || '#/login';
        if (window.location.hash === targetRoute) {
            await handleRoute();
        } else {
            window.location.hash = targetRoute;
        }
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function setupAuthFormSubmissions() {
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.onsubmit = async (e) => {
            e.preventDefault();
            const email = document.getElementById('loginEmail').value.trim();
            const password = document.getElementById('loginPassword').value;
            await executeLogin(email, password);
        };

        // Quick test login buttons
        const quickBtns = document.querySelectorAll('.btn-quick-login');
        const emailInput = document.getElementById('loginEmail');
        const passwordInput = document.getElementById('loginPassword');

        quickBtns.forEach(btn => {
            btn.onclick = async (e) => {
                e.preventDefault();
                const email = btn.dataset.email;
                const password = btn.dataset.password;
                if (emailInput) emailInput.value = email;
                if (passwordInput) passwordInput.value = password;
                await executeLogin(email, password);
            };
        });
    }

    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        registerForm.onsubmit = async (e) => {
            e.preventDefault();
            const name = document.getElementById('regName').value.trim();
            const email = document.getElementById('regEmail').value.trim();
            const password = document.getElementById('regPassword').value;
            const roomNumber = document.getElementById('regRoom').value.trim();
            const hostelBlock = document.getElementById('regBlock').value.trim().toUpperCase();

            try {
                await register(name, email, password, roomNumber, hostelBlock);
                registerForm.reset();
                showToast('Registration successful! Please login.', 'success');
                window.location.hash = '#/login';
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }
}

function setupProfileMenu() {
    const trigger = document.getElementById('profileMenuTrigger');
    const dropdown = document.getElementById('profileDropdown');

    if (trigger && dropdown) {
        trigger.onclick = (e) => {
            e.stopPropagation();
            const isHidden = dropdown.style.display === 'none' || !dropdown.style.display;
            dropdown.style.display = isHidden ? 'block' : 'none';
            trigger.setAttribute('aria-expanded', isHidden ? 'true' : 'false');
        };

        document.addEventListener('click', (e) => {
            if (!dropdown.contains(e.target) && !trigger.contains(e.target)) {
                dropdown.style.display = 'none';
                trigger.setAttribute('aria-expanded', 'false');
            }
        });
    }
}

function setupLogout() {
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.onclick = async () => {
            const dropdown = document.getElementById('profileDropdown');
            if (dropdown) dropdown.style.display = 'none';
            stopNotificationPolling();
            await logout();
        };
    }
}

let currentProfileData = null;

function setupProfileModal() {
    const modal = document.getElementById('profileModal');
    const overlay = document.getElementById('profileOverlay');
    const closeBtn = document.getElementById('closeProfileModal');
    const cancelBtn = document.getElementById('cancelProfileBtn');
    const form = document.getElementById('profileForm');
    const openBtn = document.getElementById('openProfileBtn');

    if (!modal) return;

    const closeModal = () => {
        modal.style.display = 'none';
        modal.classList.remove('active');
    };

    if (openBtn) {
        openBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            const dropdown = document.getElementById('profileDropdown');
            if (dropdown) dropdown.style.display = 'none';
            openProfileModal();
        });
    }

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);
    if (overlay) overlay.addEventListener('click', closeModal);

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && modal.style.display !== 'none') {
            closeModal();
        }
    });

    if (form) {
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            await saveProfileChanges();
        });
    }
}

async function openProfileModal() {
    const modal = document.getElementById('profileModal');
    if (!modal) return;

    modal.style.display = 'flex';
    modal.classList.add('active');

    const currentUser = getCurrentUser();
    if (currentUser) {
        populateProfileForm(currentUser);
    }

    try {
        const freshProfile = await apiRequest('/users/profile');
        currentProfileData = freshProfile;
        populateProfileForm(freshProfile);
    } catch (err) {
        showToast(err.message || 'Failed to load profile details', 'error');
    }
}

function populateProfileForm(user) {
    if (!user) return;
    const avatarEl = document.getElementById('profileModalAvatar');
    const titleEl = document.getElementById('profileModalTitle');
    const roleBadgeEl = document.getElementById('profileModalRoleBadge');
    const statusBadgeEl = document.getElementById('profileModalStatusBadge');
    const emailInput = document.getElementById('profileEmail');
    const nameInput = document.getElementById('profileName');

    const firstChar = (user.name || 'U').charAt(0).toUpperCase();
    if (avatarEl) avatarEl.textContent = firstChar;
    if (titleEl) titleEl.textContent = user.name || 'User Profile';
    if (roleBadgeEl) roleBadgeEl.textContent = user.role || '';
    if (statusBadgeEl) {
        statusBadgeEl.textContent = user.active !== false ? 'Active' : 'Inactive';
        statusBadgeEl.className = user.active !== false ? 'badge badge-success' : 'badge badge-danger';
    }

    if (emailInput) emailInput.value = user.email || '';
    if (nameInput) nameInput.value = user.name || '';

    const studentFields = document.getElementById('profileStudentFields');
    const staffFields = document.getElementById('profileStaffFields');
    const adminFields = document.getElementById('profileAdminFields');

    if (studentFields) studentFields.style.display = user.role === 'STUDENT' ? 'block' : 'none';
    if (staffFields) staffFields.style.display = user.role === 'STAFF' ? 'block' : 'none';
    if (adminFields) adminFields.style.display = user.role === 'ADMIN' ? 'block' : 'none';

    if (user.role === 'STUDENT') {
        const blockInput = document.getElementById('profileHostelBlock');
        const roomInput = document.getElementById('profileRoomNumber');
        if (blockInput) blockInput.value = user.hostelBlock || '';
        if (roomInput) roomInput.value = user.roomNumber || '';
    } else if (user.role === 'STAFF') {
        const specInput = document.getElementById('profileSpecialization');
        const availCheck = document.getElementById('profileAvailable');
        if (specInput) specInput.value = user.specializationDisplay || user.specialization || 'Maintenance';
        if (availCheck) availCheck.checked = user.available !== false;
    } else if (user.role === 'ADMIN') {
        const roleInput = document.getElementById('profileAdminRole');
        if (roleInput) roleInput.value = user.adminRole || 'System Administrator';
    }
}

async function saveProfileChanges() {
    const saveBtn = document.getElementById('saveProfileBtn');
    const nameInput = document.getElementById('profileName');
    const currentUser = getCurrentUser();
    const role = currentUser ? currentUser.role : (currentProfileData ? currentProfileData.role : null);

    const name = nameInput ? nameInput.value.trim() : '';
    if (!name) {
        showToast('Full name cannot be blank', 'error');
        if (nameInput) nameInput.focus();
        return;
    }

    const payload = { name };

    if (role === 'STUDENT') {
        const blockInput = document.getElementById('profileHostelBlock');
        const roomInput = document.getElementById('profileRoomNumber');
        const block = blockInput ? blockInput.value.trim().toUpperCase() : '';
        const room = roomInput ? roomInput.value.trim() : '';

        if (!block) {
            showToast('Hostel block is required', 'error');
            if (blockInput) blockInput.focus();
            return;
        }
        if (!room) {
            showToast('Room number is required', 'error');
            if (roomInput) roomInput.focus();
            return;
        }
        payload.hostelBlock = block;
        payload.roomNumber = room;
    } else if (role === 'STAFF') {
        const availCheck = document.getElementById('profileAvailable');
        if (availCheck) {
            payload.available = availCheck.checked;
        }
    }

    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.textContent = 'Saving...';
    }

    try {
        const resp = await apiRequest('/users/profile', {
            method: 'PUT',
            body: payload
        });

        const updatedUser = resp.user || resp;
        currentProfileData = updatedUser;
        setCurrentUser(updatedUser);
        updateProfileMenu(updatedUser);
        if (updatedUser && updatedUser.role === 'STAFF') {
            const availToggle = document.getElementById('availabilityToggle');
            if (availToggle && typeof updatedUser.available === 'boolean') {
                availToggle.checked = updatedUser.available;
            }
        }

        showToast(resp.message || 'Profile updated successfully!', 'success');

        const modal = document.getElementById('profileModal');
        if (modal) {
            modal.style.display = 'none';
            modal.classList.remove('active');
        }
    } catch (err) {
        showToast(err.message || 'Failed to update profile', 'error');
    } finally {
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.textContent = 'Save Changes';
        }
    }
}

async function handleRoute() {
    const hash = window.location.hash || '#/login';
    const user = getCurrentUser() || await checkAuth();

    const navbar = document.getElementById('navbar');
    const footer = document.getElementById('appFooter');
    const authView = document.getElementById('authView');
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    const studentDash = document.getElementById('studentDashboard');
    const adminDash = document.getElementById('adminDashboard');
    const staffDash = document.getElementById('staffDashboard');
    const historyView = document.getElementById('historyView');
    const navLinks = document.getElementById('navLinks');

    // Hide all main containers
    if (studentDash) studentDash.style.display = 'none';
    if (adminDash) adminDash.style.display = 'none';
    if (staffDash) staffDash.style.display = 'none';
    if (historyView) historyView.style.display = 'none';

    // Unauthenticated routes
    if (!user || hash === '#/login' || hash === '#/register') {
        if (navbar) navbar.style.display = 'none';
        if (footer) footer.style.display = 'none';
        if (authView) authView.style.display = 'flex';

        if (hash === '#/register') {
            if (loginForm) loginForm.style.display = 'none';
            if (registerForm) registerForm.style.display = 'block';
        } else {
            if (loginForm) loginForm.style.display = 'block';
            if (registerForm) registerForm.style.display = 'none';
        }
        return;
    }

    // Role-based route guard: redirect to role default if hash is unauthorized or legacy
    const allowedRoutes = ROLE_ROUTES[user.role] || [];
    if (!allowedRoutes.includes(hash)) {
        window.location.hash = DEFAULT_ROUTE[user.role];
        return;
    }

    // Show authenticated chrome
    if (authView) authView.style.display = 'none';
    if (navbar) navbar.style.display = 'flex';
    if (footer) footer.style.display = (user.role === 'ADMIN') ? 'block' : 'none';

    // Populate user info in profile menu
    updateProfileMenu(user);

    // Render single-level role navigation
    renderNavLinks(user.role, hash, navLinks);

    // Initialize notification polling
    initNotifications();

    // Render corresponding view
    if (hash === '#/history') {
        if (historyView) historyView.style.display = 'block';
        initHistoryView(user.role);
    } else if (user.role === 'STUDENT') {
        if (studentDash) studentDash.style.display = 'block';
        initStudentView(hash === '#/new');
    } else if (user.role === 'STAFF') {
        if (staffDash) staffDash.style.display = 'block';
        initStaffView(hash === '#/completed');
    } else if (user.role === 'ADMIN') {
        if (adminDash) adminDash.style.display = 'block';
        const subroute = hash.replace('#/', ''); // triage, complaints, analytics, staff
        initAdminView(subroute);
    }
}

function updateProfileMenu(user) {
    const avatarEl = document.getElementById('userAvatar');
    const userNameEl = document.getElementById('userName');
    const roleBadgeEl = document.getElementById('userRoleBadge');
    const dropdownNameEl = document.getElementById('dropdownUserName');
    const dropdownEmailEl = document.getElementById('dropdownUserEmail');

    const firstChar = (user.name || 'U').charAt(0).toUpperCase();
    if (avatarEl) avatarEl.textContent = firstChar;
    if (userNameEl) userNameEl.textContent = user.name || 'User';
    if (roleBadgeEl) roleBadgeEl.textContent = user.role || '';
    if (dropdownNameEl) dropdownNameEl.textContent = user.name || '';
    if (dropdownEmailEl) dropdownEmailEl.textContent = user.email || '';
}

function renderNavLinks(role, activeHash, container) {
    if (!container) return;

    let linksHtml = '';
    if (role === 'STUDENT') {
        linksHtml = `
            <a href="#/mine" class="nav-link ${activeHash === '#/mine' ? 'active' : ''}">${getIcon('clipboard', 'nav-icon', 15)} My Complaints</a>
            <a href="#/new" class="nav-link ${activeHash === '#/new' ? 'active' : ''}">${getIcon('plus', 'nav-icon', 15)} New Complaint</a>
            <a href="#/history" class="nav-link ${activeHash === '#/history' ? 'active' : ''}">${getIcon('history', 'nav-icon', 15)} History</a>
        `;
    } else if (role === 'STAFF') {
        linksHtml = `
            <a href="#/tasks" class="nav-link ${activeHash === '#/tasks' ? 'active' : ''}">${getIcon('wrench', 'nav-icon', 15)} My Tasks</a>
            <a href="#/completed" class="nav-link ${activeHash === '#/completed' ? 'active' : ''}">${getIcon('check', 'nav-icon', 15)} Completed</a>
            <a href="#/history" class="nav-link ${activeHash === '#/history' ? 'active' : ''}">${getIcon('history', 'nav-icon', 15)} Task History</a>
        `;
    } else if (role === 'ADMIN') {
        linksHtml = `
            <a href="#/triage" class="nav-link ${activeHash === '#/triage' ? 'active' : ''}">${getIcon('clipboard', 'nav-icon', 15)} Triage</a>
            <a href="#/complaints" class="nav-link ${activeHash === '#/complaints' ? 'active' : ''}">${getIcon('chart', 'nav-icon', 15)} All Complaints</a>
            <a href="#/history" class="nav-link ${activeHash === '#/history' ? 'active' : ''}">${getIcon('history', 'nav-icon', 15)} Audit History</a>
            <a href="#/analytics" class="nav-link ${activeHash === '#/analytics' ? 'active' : ''}">${getIcon('chart', 'nav-icon', 15)} Analytics</a>
            <a href="#/staff" class="nav-link ${activeHash === '#/staff' ? 'active' : ''}">${getIcon('users', 'nav-icon', 15)} Users</a>
        `;
    }

    container.innerHTML = linksHtml;
}

/**
 * Normalizes priority values (strings 'HIGH', 'MEDIUM', 'LOW' or integers 1, 2, 3)
 * into a standard object with label, CSS class, numeric level, and enum key.
 *
 * @param {string|number} priority
 * @returns {{ label: string, class: string, level: number, key: string }}
 */
export function getPriorityInfo(priority) {
    if (priority === null || priority === undefined || priority === '') {
        return { label: 'Low', class: 'priority-low', level: 3, key: 'LOW' };
    }
    const p = String(priority).trim().toUpperCase();
    if (p === 'HIGH' || p === '1') {
        return { label: 'High', class: 'priority-high', level: 1, key: 'HIGH' };
    }
    if (p === 'MEDIUM' || p === '2') {
        return { label: 'Medium', class: 'priority-medium', level: 2, key: 'MEDIUM' };
    }
    if (p === 'LOW' || p === '3') {
        return { label: 'Low', class: 'priority-low', level: 3, key: 'LOW' };
    }
    return { label: 'Low', class: 'priority-low', level: 3, key: 'LOW' };
}

/**
 * Normalizes ComplaintStatus enum strings into user-friendly title case strings.
 *
 * @param {string} status
 * @returns {string} e.g. "In Progress", "Resolved"
 */
export function formatStatus(status) {
    if (!status) return '';
    const map = {
        'NEW': 'New',
        'ASSIGNED': 'Assigned',
        'IN_PROGRESS': 'In Progress',
        'RESOLVED': 'Resolved',
        'CLOSED': 'Closed',
        'REOPENED': 'Reopened'
    };
    return map[String(status).trim().toUpperCase()] || status;
}
