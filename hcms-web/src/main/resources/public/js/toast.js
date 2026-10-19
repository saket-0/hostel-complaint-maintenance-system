/**
 * Toast Notification System.
 * Displays temporary toast alerts at the bottom right.
 */
let activeToastTimeout = null;

export function dismissToast() {
    if (activeToastTimeout) {
        clearTimeout(activeToastTimeout);
        activeToastTimeout = null;
    }
    const container = document.getElementById('toastContainer');
    if (container) {
        container.innerHTML = '';
    }
}

export function showToast(message, type = 'info', duration = 3000) {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    // Show at most one toast at a time (new replaces old)
    dismissToast();

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.setAttribute('role', 'alert');
    toast.setAttribute('aria-live', 'assertive');

    let icon = 'ℹ️';
    if (type === 'success') icon = '✅';
    if (type === 'error') icon = '⚠️';

    toast.innerHTML = `<span class="toast-icon">${icon}</span> <span class="toast-message">${escapeHtml(message)}</span>`;
    container.appendChild(toast);

    activeToastTimeout = setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(16px)';
        toast.style.transition = 'all 0.3s ease-in';
        setTimeout(() => {
            if (toast.parentNode === container) {
                toast.remove();
            }
        }, 300);
    }, duration);
}

export function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
