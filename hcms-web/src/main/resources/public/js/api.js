/**
 * API client module for HCMS.
 * Handles HTTP requests, JWT authorization headers, and uniform error handling.
 */
import { showToast } from './toast.js';

const API_BASE = '/api/v1';

export function getToken() {
    return localStorage.getItem('hcms_token');
}

export function setToken(token) {
    if (token) {
        localStorage.setItem('hcms_token', token);
    } else {
        localStorage.removeItem('hcms_token');
    }
}

export async function apiRequest(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;
    const token = getToken();

    const headers = {
        'Accept': 'application/json',
        ...(options.headers || {})
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    if (options.body && !(options.body instanceof FormData) && typeof options.body === 'object') {
        headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(options.body);
    }

    const config = {
        ...options,
        headers,
        credentials: 'include'
    };

    try {
        const response = await fetch(url, config);

        // Handle CSV or text responses
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('text/csv')) {
            if (!response.ok) throw new Error('Failed to download CSV');
            return await response.text();
        }

        let data = null;
        if (response.status !== 204) {
            try {
                data = await response.json();
            } catch (e) {
                // not json
            }
        }

        if (!response.ok) {
            const errorMsg = (data && data.message) || (data && data.error) || `HTTP ${response.status}: Request failed`;
            
            if (response.status === 401) {
                setToken(null);
                window.location.hash = '#/login';
            }
            
            throw new Error(errorMsg);
        }

        return data;
    } catch (err) {
        console.error(`API Error [${endpoint}]:`, err);
        throw err;
    }
}
