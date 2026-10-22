package edu.vit.hcms.web.controller;

import at.favre.lib.crypto.bcrypt.BCrypt;
import edu.vit.hcms.core.exception.DuplicateEntryException;
import edu.vit.hcms.core.exception.ResourceNotFoundException;
import edu.vit.hcms.core.exception.ValidationException;
import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.core.model.User;
import edu.vit.hcms.persistence.dao.ComplaintDao;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.web.dto.CreateStudentRequest;
import edu.vit.hcms.web.dto.UpdateStudentRequest;
import edu.vit.hcms.web.dto.UpdateStudentStatusRequest;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Controller managing administrative actions on hostel students:
 * directory listing with search & block filters, student onboarding with temporary passwords,
 * room reassignments, and soft deletion / deactivation with audit trails.
 */
public class StudentAdminController {

    private static final Logger log = LoggerFactory.getLogger(StudentAdminController.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserDao userDao;
    private final ComplaintDao complaintDao;

    public StudentAdminController(UserDao userDao, ComplaintDao complaintDao) {
        this.userDao = userDao;
        this.complaintDao = complaintDao;
    }

    /**
     * GET /api/students & GET /api/v1/admin/students
     * Lists students matching search query and hostel block filter.
     */
    public void listStudents(Context ctx) {
        String query = ctx.queryParam("q");
        if (query == null || query.isBlank()) {
            query = ctx.queryParam("search");
        }
        String block = ctx.queryParam("block");
        if (block != null && block.equalsIgnoreCase("all")) {
            block = null;
        }

        int page = 0;
        String pageStr = ctx.queryParam("page");
        if (pageStr != null && !pageStr.isBlank()) {
            try {
                page = Math.max(0, Integer.parseInt(pageStr.trim()));
            } catch (NumberFormatException ignored) {}
        }

        int size = 20;
        String sizeStr = ctx.queryParam("size");
        if (sizeStr != null && !sizeStr.isBlank()) {
            try {
                size = Math.min(100, Math.max(1, Integer.parseInt(sizeStr.trim())));
            } catch (NumberFormatException ignored) {}
        }

        List<Student> students = userDao.findAllStudents(query, block, page * size, size);
        long total = userDao.countStudents(query, block);
        Map<Long, Long> activeComplaintsMap = complaintDao.studentActiveComplaintCounts();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Student s : students) {
            long activeCount = activeComplaintsMap.getOrDefault(s.getUserId(), 0L);
            result.add(toStudentMap(s, activeCount));
        }

        ctx.json(Map.of(
                "students", result,
                "total", total,
                "page", page,
                "size", size
        ));
    }

    /**
     * GET /api/students/{id}
     * Returns a single student profile with active complaints count.
     */
    public void getStudent(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Student student = findStudent(id);
        long activeCount = complaintDao.countActiveComplaintsByStudentId(id);
        ctx.json(Map.of("student", toStudentMap(student, activeCount)));
    }

    /**
     * POST /api/students
     * Creates a new student with temporary password and forced password change on first login.
     */
    public void createStudent(Context ctx) {
        CreateStudentRequest req = ctx.bodyAsClass(CreateStudentRequest.class);
        validateCreateStudent(req);

        // Check email uniqueness
        if (userDao.findByEmail(req.email().trim().toLowerCase()).isPresent()) {
            throw new DuplicateEntryException("A user with email '" + req.email().trim().toLowerCase() + "' already exists");
        }

        // Check registration number uniqueness
        if (userDao.findByRegistrationNumber(req.registrationNumber().trim().toUpperCase()).isPresent()) {
            throw new DuplicateEntryException("Registration number '" + req.registrationNumber().trim().toUpperCase() + "' is already in use");
        }

        String tempPassword = req.password() != null && !req.password().isBlank() ? req.password().trim() : "password123";
        if (tempPassword.length() < 6) {
            throw new ValidationException("password", "Password must be at least 6 characters");
        }
        String passwordHash = BCrypt.withDefaults().hashToString(12, tempPassword.toCharArray());

        Student student = new Student();
        student.setName(req.name().trim());
        student.setEmail(req.email().trim().toLowerCase());
        student.setPasswordHash(passwordHash);
        student.setRegistrationNumber(req.registrationNumber().trim().toUpperCase());
        student.setHostelBlock(req.hostelBlock().trim().toUpperCase());
        student.setRoomNumber(req.roomNumber().trim());
        if (req.phone() != null && !req.phone().isBlank()) {
            student.setPhone(req.phone().trim());
        }
        student.setActive(true);
        student.setForcePasswordChange(true);

        userDao.save(student);

        Long adminId = getActingAdminId(ctx);
        userDao.saveUserHistory(
                student.getUserId(),
                "STUDENT_CREATED",
                adminId,
                "Admin added student " + student.getName() + " (" + student.getRegistrationNumber() + ") to Block " + student.getHostelBlock() + ", Room " + student.getRoomNumber()
        );

        log.info("Student created successfully by admin {}: {} (ID {})", adminId, student.getEmail(), student.getUserId());
        ctx.status(201).json(Map.of(
                "message", "Student created successfully",
                "student", toStudentMap(student, 0L)
        ));
    }

    /**
     * PUT /api/students/{id}
     * Updates student details and handles Room Changes with audit history.
     */
    public void updateStudent(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Student student = findStudent(id);
        UpdateStudentRequest req = ctx.bodyAsClass(UpdateStudentRequest.class);

        boolean roomChanged = false;
        String oldLocation = "Block " + student.getHostelBlock() + " Room " + student.getRoomNumber();

        if (req.name() != null) {
            if (req.name().trim().isBlank()) {
                throw new ValidationException("name", "Full name cannot be blank");
            }
            student.setName(req.name().trim());
        }

        if (req.email() != null && !req.email().trim().equalsIgnoreCase(student.getEmail())) {
            String newEmail = req.email().trim().toLowerCase();
            if (!EMAIL_PATTERN.matcher(newEmail).matches()) {
                throw new ValidationException("email", "Valid email address is required");
            }
            userDao.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getUserId().equals(id)) {
                    throw new DuplicateEntryException("A user with email '" + newEmail + "' already exists");
                }
            });
            student.setEmail(newEmail);
        }

        if (req.registrationNumber() != null && !req.registrationNumber().trim().equalsIgnoreCase(student.getRegistrationNumber())) {
            String newRegNo = req.registrationNumber().trim().toUpperCase();
            if (newRegNo.isBlank()) {
                throw new ValidationException("registrationNumber", "Registration number cannot be blank");
            }
            userDao.findByRegistrationNumber(newRegNo).ifPresent(existing -> {
                if (!existing.getUserId().equals(id)) {
                    throw new DuplicateEntryException("Registration number '" + newRegNo + "' is already in use");
                }
            });
            student.setRegistrationNumber(newRegNo);
        }

        if (req.hostelBlock() != null && !req.hostelBlock().trim().equalsIgnoreCase(student.getHostelBlock())) {
            String newBlock = req.hostelBlock().trim().toUpperCase();
            if (newBlock.isBlank()) {
                throw new ValidationException("hostelBlock", "Hostel block cannot be blank");
            }
            student.setHostelBlock(newBlock);
            roomChanged = true;
        }

        if (req.roomNumber() != null && !req.roomNumber().trim().equalsIgnoreCase(student.getRoomNumber())) {
            String newRoom = req.roomNumber().trim();
            if (newRoom.isBlank()) {
                throw new ValidationException("roomNumber", "Room number cannot be blank");
            }
            student.setRoomNumber(newRoom);
            roomChanged = true;
        }

        if (req.phone() != null) {
            student.setPhone(req.phone().trim().isEmpty() ? null : req.phone().trim());
        }

        userDao.update(student);

        Long adminId = getActingAdminId(ctx);
        String action = roomChanged ? "ROOM_CHANGED" : "STUDENT_UPDATED";
        String newLocation = "Block " + student.getHostelBlock() + " Room " + student.getRoomNumber();
        String note = roomChanged
                ? "Admin reassigned student room from " + oldLocation + " to " + newLocation
                : "Admin updated student profile details for " + student.getName();

        userDao.saveUserHistory(id, action, adminId, note);

        long activeCount = complaintDao.countActiveComplaintsByStudentId(id);
        log.info("Student updated by admin {}: ID {}, action {}", adminId, id, action);
        ctx.json(Map.of(
                "message", "Student details updated successfully",
                "student", toStudentMap(student, activeCount)
        ));
    }

    /**
     * PATCH /api/students/{id}/status
     * Toggles student active status (soft-delete / reactivation).
     */
    public void updateStatus(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Student student = findStudent(id);
        UpdateStudentStatusRequest req = ctx.bodyAsClass(UpdateStudentStatusRequest.class);

        boolean newActive;
        if (req.active() != null) {
            newActive = req.active();
        } else if (req.status() != null) {
            newActive = "ACTIVE".equalsIgnoreCase(req.status().trim());
        } else {
            throw new ValidationException("active", "Either 'active' boolean or 'status' string must be provided");
        }

        student.setActive(newActive);
        userDao.update(student);

        Long adminId = getActingAdminId(ctx);
        String action = newActive ? "STUDENT_ACTIVATED" : "STUDENT_DEACTIVATED";
        String note = newActive
                ? "Student account reactivated by admin"
                : "Student account deactivated (soft-deleted) by admin. Relational history preserved.";

        userDao.saveUserHistory(id, action, adminId, note);

        long activeCount = complaintDao.countActiveComplaintsByStudentId(id);
        log.info("Student ID {} status updated to {} by admin {}", id, newActive ? "ACTIVE" : "DEACTIVATED", adminId);
        ctx.json(Map.of(
                "message", "Student account " + (newActive ? "activated" : "deactivated") + " successfully",
                "active", newActive,
                "status", newActive ? "ACTIVE" : "DEACTIVATED",
                "student", toStudentMap(student, activeCount)
        ));
    }

    private Student findStudent(Long id) {
        User user = userDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));
        if (!(user instanceof Student student)) {
            throw new ValidationException("id", "User ID " + id + " is not a student");
        }
        return student;
    }

    private void validateCreateStudent(CreateStudentRequest req) {
        if (req.name() == null || req.name().trim().isBlank()) {
            throw new ValidationException("name", "Full name is required");
        }
        if (req.email() == null || !EMAIL_PATTERN.matcher(req.email().trim()).matches()) {
            throw new ValidationException("email", "A valid email address is required");
        }
        if (req.registrationNumber() == null || req.registrationNumber().trim().isBlank()) {
            throw new ValidationException("registrationNumber", "Registration number is required");
        }
        if (req.hostelBlock() == null || req.hostelBlock().trim().isBlank()) {
            throw new ValidationException("hostelBlock", "Hostel block is required (e.g. A, B, C)");
        }
        if (req.roomNumber() == null || req.roomNumber().trim().isBlank()) {
            throw new ValidationException("roomNumber", "Room number is required (e.g. 101, 204)");
        }
    }

    private Long getActingAdminId(Context ctx) {
        Long adminId = ctx.attribute("userId");
        if (adminId != null) return adminId;
        return 1L; // Fallback to chief warden if attribute not populated
    }

    public static Map<String, Object> toStudentMap(Student s, long activeComplaints) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", s.getUserId());
        m.put("studentId", s.getUserId());
        m.put("name", s.getName());
        m.put("email", s.getEmail());
        m.put("registrationNumber", s.getRegistrationNumber() != null ? s.getRegistrationNumber() : "");
        m.put("hostelBlock", s.getHostelBlock() != null ? s.getHostelBlock() : "");
        m.put("roomNumber", s.getRoomNumber() != null ? s.getRoomNumber() : "");
        m.put("phone", s.getPhone() != null ? s.getPhone() : "");
        m.put("active", s.isActive());
        m.put("status", s.isActive() ? "ACTIVE" : "DEACTIVATED");
        m.put("forcePasswordChange", s.isForcePasswordChange());
        m.put("activeComplaints", activeComplaints);
        m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
        return m;
    }
}
