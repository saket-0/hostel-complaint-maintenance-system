/**
 * System Metrics Poller Module.
 * Periodically fetches JVM statistics from /api/v1/metrics-lite
 * and updates the "Built with Java" footer.
 */
import { apiRequest } from './api.js';

let metricsInterval = null;

export function initMetricsFooter() {
    updateMetrics();
    if (metricsInterval) clearInterval(metricsInterval);
    metricsInterval = setInterval(updateMetrics, 5000);
}

export function stopMetricsPolling() {
    if (metricsInterval) {
        clearInterval(metricsInterval);
        metricsInterval = null;
    }
}

async function updateMetrics() {
    const javaVersionEl = document.getElementById('javaVersion');
    const uptimeEl = document.getElementById('uptime');
    const threadCountEl = document.getElementById('threadCount');
    const queueDepthEl = document.getElementById('queueDepth');

    try {
        const metrics = await apiRequest('/metrics-lite');
        if (metrics) {
            if (javaVersionEl && metrics.javaVersion) {
                javaVersionEl.textContent = `${metrics.javaVersion} (${metrics.javaVendor || 'OpenJDK'})`;
            }
            if (uptimeEl && metrics.uptime) {
                uptimeEl.textContent = metrics.uptime;
            }
            if (threadCountEl && metrics.activeThreads !== undefined) {
                threadCountEl.textContent = `${metrics.activeThreads} active`;
            }
            if (queueDepthEl && metrics.notificationQueueDepth !== undefined) {
                queueDepthEl.textContent = `${metrics.notificationQueueDepth} msgs`;
            }
        }
    } catch (e) {
        // server might be booting
    }
}
