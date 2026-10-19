/**
 * HCMS Single Page Application Root.
 * Manages hash routing, role-based view switching, and lifecycle initialization.
 */
import { checkAuth, login, register, logout, getCurrentUser } from './auth.js';
import { showToast } from './toast.js';
import { initStudentView } from './student.js';
import { initAdminView } from './admin.js';
import { initStaffView } from './staff.js';
import { initNotifications, stopNotificationPolling } from './notifications.js';
import { initMetricsFooter } from './metrics.js';

document.addEventListener('DOMContentLoaded', async () => {
    initMetricsFooter();
    setupAuthFormToggles();
    setupAuthFormSubmissions();
    setupLogout();

    window.addEventListener('hashchange', handleRoute);
    
    // Check initial session
    const user = await checkAuth();
    if (user) {
        if (!window.location.hash || window.location.hash === '#/login' || window.location.hash === '#/register') {
            window.location.hash = '#/dashboard';
        } else {
            handleRoute();
        }
    } else {
        window.location.hash = '#/login';
        handleRoute();
    }
});

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
                window.location.hash = '#/dashboard';
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
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
                window.location.hash = '#/login';
            } catch (err) {
                showToast(err.message, 'error');
            }
        };
    }
}

function setupLogout() {
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.onclick = async () => {
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
    const userNameEl = document.getElementById('userName');

    // Default hide all views
    studentDash.style.display = 'none';
    adminDash.style.display = 'none';
    staffDash.style.display = 'none';

    if (!user || hash === '#/login' || hash === '#/register') {
        navbar.style.display = 'none';
        footer.style.display = 'none';
        authView.style.display = 'flex';

        if (hash === '#/register') {
            loginForm.style.display = 'none';
            registerForm.style.display = 'block';
        } else {
            loginForm.style.display = 'block';
            registerForm.style.display = 'none';
        }
        return;
    }

    // Authenticated views
    authView.style.display = 'none';
    navbar.style.display = 'flex';
    footer.style.display = 'block';

    if (userNameEl) {
        userNameEl.textContent = `${user.name} (${user.role})`;
    }

    initNotifications();

    if (user.role === 'STUDENT') {
        studentDash.style.display = 'block';
        initStudentView();
    } else if (user.role === 'ADMIN') {
        adminDash.style.display = 'block';
        initAdminView();
    } else if (user.role === 'STAFF') {
        staffDash.style.display = 'block';
        initStaffView();
    }
}
