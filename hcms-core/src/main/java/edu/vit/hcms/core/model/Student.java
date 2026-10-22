package edu.vit.hcms.core.model;

import edu.vit.hcms.core.enums.Role;

import java.util.Set;

/**
 * Represents a hostel student who can file complaints.
 * Extends {@link User} and adds room/hostel information.
 * Demonstrates concrete subclass with method overriding.
 */
public class Student extends User {

    private String roomNumber;
    private String hostelBlock;
    private String registrationNumber;
    private String phone;

    /** Default constructor. */
    public Student() {
        setRole(Role.STUDENT);
    }

    /**
     * Full constructor for creating a Student with all fields.
     *
     * @param userId       unique user identifier
     * @param name         student's full name
     * @param email        VIT email address
     * @param passwordHash BCrypt-hashed password
     * @param roomNumber   hostel room number (e.g., "301")
     * @param hostelBlock  hostel block letter (e.g., "A")
     */
    public Student(Long userId, String name, String email, String passwordHash,
                   String roomNumber, String hostelBlock) {
        this(userId, name, email, passwordHash, roomNumber, hostelBlock, null, null);
    }

    /**
     * Extended constructor with registration number and phone.
     */
    public Student(Long userId, String name, String email, String passwordHash,
                   String roomNumber, String hostelBlock, String registrationNumber, String phone) {
        super(userId, name, email, passwordHash, Role.STUDENT);
        this.roomNumber = roomNumber;
        this.hostelBlock = hostelBlock;
        this.registrationNumber = registrationNumber;
        this.phone = phone;
    }

    /**
     * Constructor without userId for new student registration.
     */
    public Student(String name, String email, String passwordHash,
                   String roomNumber, String hostelBlock) {
        this(name, email, passwordHash, roomNumber, hostelBlock, null, null);
    }

    /**
     * Constructor without userId with registration number and phone.
     */
    public Student(String name, String email, String passwordHash,
                   String roomNumber, String hostelBlock, String registrationNumber, String phone) {
        super(name, email, passwordHash, Role.STUDENT);
        this.roomNumber = roomNumber;
        this.hostelBlock = hostelBlock;
        this.registrationNumber = registrationNumber;
        this.phone = phone;
    }

    @Override
    public String getDashboardView() {
        return "student-dashboard";
    }

    @Override
    public Set<String> getPermissions() {
        return Set.of(
                "complaint:create",
                "complaint:read:own",
                "complaint:close:own",
                "complaint:reopen:own",
                "feedback:create",
                "comment:create",
                "notification:read"
        );
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getHostelBlock() {
        return hostelBlock;
    }

    public void setHostelBlock(String hostelBlock) {
        this.hostelBlock = hostelBlock;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
