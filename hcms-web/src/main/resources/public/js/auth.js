/**
 * Authentication module for HCMS.
 * Manages user state, login, registration, and logout flows.
 */
import { apiRequest, setToken, getToken } from './api.js';
import { showToast } from './toast.js';

let currentUser = null;

export function getCurrentUser() {
    return currentUser;
}

export function setCurrentUser(user) {
    currentUser = user;
}

export async function checkAuth() {
    const token = getToken();
    if (!token) return null;

    try {
        const user = await apiRequest('/auth/me');
        currentUser = user;
        return user;
    } catch (err) {
        currentUser = null;
        setToken(null);
        return null;
    }
}

export async function login(email, password) {
    const data = await apiRequest('/auth/login', {
        method: 'POST',
        body: { email, password }
    });

    if (data && data.token) {
        setToken(data.token);
        currentUser = data.user;
        showToast(`Welcome back, ${data.user.name}!`, 'success');
        return data.user;
    }
    throw new Error('Login failed: Invalid server response');
}

export async function register(name, email, password, roomNumber, hostelBlock) {
    const data = await apiRequest('/auth/register', {
        method: 'POST',
        body: { name, email, password, roomNumber, hostelBlock }
    });
    showToast('Registration successful! Please login.', 'success');
    return data;
}

export async function logout() {
    try {
        await apiRequest('/auth/logout', { method: 'POST' });
    } catch (e) {
        // ignore logout errors
    } finally {
        setToken(null);
        currentUser = null;
        showToast('Logged out successfully', 'info');
        window.location.hash = '#/login';
    }
}
