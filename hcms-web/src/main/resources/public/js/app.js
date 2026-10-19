/**
 * HCMS Single Page Application Root.
 * Manages single-level role navigation, hash routing, view switching, and lifecycle initialization.
 */
import { checkAuth, login, register, logout, getCurrentUser } from './auth.js';
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
    setupLogout();

    window.addEventListener('hashchange', handleRoute);

    // Initial session check
    const user = await checkAuth();
    if (user) {
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

function setupAuthFormSubmissions() {
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.onsubmit = async (e) => {
            e.preventDefault();
            const email = document.getElementById('loginEmail').value.trim();
            const password = document.getElementById('loginPassword').value;

            try {
                const user = await login(email, password);
                window.location.hash = DEFAULT_ROUTE[user.role] || '#/login';
            } catch (err) {
                showToast(err.message, 'error');
            }
        };

        // Quick test login buttons
        const quickBtns = document.querySelectorAll('.btn-quick-login');
        const emailInput = document.getElementById('loginEmail');
        const passwordInput = document.getElementById('loginPassword');

        quickBtns.forEach(btn => {
            btn.onclick = (e) => {
                e.preventDefault();
                const email = btn.dataset.email;
                const password = btn.dataset.password;
                if (emailInput) emailInput.value = email;
                if (passwordInput) passwordInput.value = password;
                if (typeof loginForm.requestSubmit === 'function') {
                    loginForm.requestSubmit();
                } else {
                    loginForm.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
                }
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
    if (footer) footer.style.display = 'block';

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
            <a href="#/staff" class="nav-link ${activeHash === '#/staff' ? 'active' : ''}">${getIcon('users', 'nav-icon', 15)} Staff</a>
        `;
    }

    container.innerHTML = linksHtml;
}
