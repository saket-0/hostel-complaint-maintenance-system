package edu.vit.hcms.service.analytics;

import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.core.model.MaintenanceStaff;
import edu.vit.hcms.persistence.dao.ComplaintDao;
import edu.vit.hcms.persistence.dao.UserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics service providing dashboard statistics.
 * Demonstrates extensive use of Java Collections and Stream API:
 * <ul>
 *   <li>EnumMap for status counts</li>
 *   <li>TreeMap for time-series data</li>
 *   <li>Streams and Collectors for grouping, averaging, summarizing</li>
 *   <li>Comparator chains for multi-field sorting</li>
 * </ul>
 */
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final ComplaintDao complaintDao;
    private final UserDao userDao;

    public AnalyticsService(ComplaintDao complaintDao, UserDao userDao) {
        this.complaintDao = complaintDao;
        this.userDao = userDao;
    }

    /**
     * Returns comprehensive dashboard analytics.
     */
    public Map<String, Object> getDashboardAnalytics() {
        Map<String, Object> analytics = new LinkedHashMap<>();

        // Status distribution: Showcase EnumMap
        EnumMap<edu.vit.hcms.core.enums.ComplaintStatus, Long> statusCounts = new EnumMap<>(edu.vit.hcms.core.enums.ComplaintStatus.class);
        for (edu.vit.hcms.core.enums.ComplaintStatus s : edu.vit.hcms.core.enums.ComplaintStatus.values()) {
            statusCounts.put(s, 0L);
        }
        complaintDao.countByStatus().forEach((k, v) -> {
            try {
                statusCounts.put(edu.vit.hcms.core.enums.ComplaintStatus.valueOf(k), v);
            } catch (IllegalArgumentException ignored) {}
        });
        analytics.put("byStatus", statusCounts);

        // Category distribution
        analytics.put("byCategory", complaintDao.countByCategory());

        // Priority distribution
        analytics.put("byPriority", complaintDao.countByPriority());

        // Average resolution time
        analytics.put("avgResolutionHours", Math.round(complaintDao.averageResolutionHours() * 10.0) / 10.0);

        // SLA breach rate
        analytics.put("slaBreachRate", Math.round(complaintDao.slaBreachRate() * 10.0) / 10.0);

        // Complaints per hostel block
        analytics.put("byHostelBlock", complaintDao.countByHostelBlock());

        // Daily trend (last 30 days) using TreeMap for sorted dates
        TreeMap<String, Long> trend = new TreeMap<>(complaintDao.dailyTrend(30));
        analytics.put("dailyTrend", trend);

        // Staff workload and ratings
        Map<Long, Long> workload = complaintDao.staffWorkload();
        Map<Long, Double> ratings = complaintDao.staffAverageRating();
        List<MaintenanceStaff> allStaff = userDao.findAllStaff(null);

        // Build staff summary using Streams
        List<Map<String, Object>> staffSummary = allStaff.stream()
                .map(staff -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", staff.getUserId());
                    entry.put("name", staff.getName());
                    entry.put("specialization", staff.getSpecialization().getDisplayName());
                    entry.put("available", staff.isAvailable());
                    entry.put("activeComplaints", workload.getOrDefault(staff.getUserId(), 0L));
                    entry.put("avgRating", ratings.getOrDefault(staff.getUserId(), 0.0));
                    return entry;
                })
                // Comparator chain: sort by active complaints descending, then by name
                .sorted(Comparator
                        .<Map<String, Object>, Long>comparing(m -> (Long) m.get("activeComplaints"))
                        .reversed()
                        .thenComparing(m -> (String) m.get("name")))
                .collect(Collectors.toList());

        analytics.put("staffSummary", staffSummary);

        // Total counts
        analytics.put("totalComplaints", complaintDao.findAll().size());

        return analytics;
    }

    /**
     * Exports complaints as CSV data (list of string arrays).
     */
    public List<String[]> exportComplaintsAsCsv() {
        List<Complaint> complaints = complaintDao.findAll();
        List<String[]> rows = new ArrayList<>();

        // Header row
        rows.add(new String[]{"Complaint ID", "Category", "Priority", "Status",
                "Created Date", "Updated Date", "Resolved Date", "Due By"});

        // Data rows using Stream
        complaints.stream()
                .map(c -> new String[]{
                        c.getComplaintId(),
                        c.getCategory().getDisplayName(),
                        c.getPriority().getDisplayName(),
                        c.getStatus().getDisplayName(),
                        c.getCreatedDate().toString(),
                        c.getUpdatedDate().toString(),
                        c.getResolvedDate() != null ? c.getResolvedDate().toString() : "",
                        c.getDueBy() != null ? c.getDueBy().toString() : ""
                })
                .forEach(rows::add);

        return rows;
    }
}
