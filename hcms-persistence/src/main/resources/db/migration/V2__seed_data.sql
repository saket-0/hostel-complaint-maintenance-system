-- V2: Seed data for dev profile
-- 1 admin, 4 staff, 10 students, 40 complaints across all statuses
-- All passwords are BCrypt hash of 'password123'

-- BCrypt hash of 'password123'
SET @pwd = '$2a$12$7f8SSpbLSfJGaXvNRMODLuu6jSoCH9QJvkHWSVMfxema4J5Bp3H66';

-- ============ ADMIN ============
INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Dr. Ramesh Kumar', 'admin@vit.ac.in', @pwd, 'ADMIN', TRUE);
SET @admin_id = LAST_INSERT_ID();
INSERT INTO admins (admin_id, admin_role) VALUES (@admin_id, 'Chief Warden');

-- ============ STAFF ============
INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Arun Electrician', 'staff.arun@vit.ac.in', @pwd, 'STAFF', TRUE);
SET @staff1 = LAST_INSERT_ID();
INSERT INTO staff (staff_id, specialization, available) VALUES (@staff1, 'ELECTRICAL', TRUE);

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Bala Plumber', 'staff.bala@vit.ac.in', @pwd, 'STAFF', TRUE);
SET @staff2 = LAST_INSERT_ID();
INSERT INTO staff (staff_id, specialization, available) VALUES (@staff2, 'PLUMBING', TRUE);

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Chandra IT', 'staff.chandra@vit.ac.in', @pwd, 'STAFF', TRUE);
SET @staff3 = LAST_INSERT_ID();
INSERT INTO staff (staff_id, specialization, available) VALUES (@staff3, 'NETWORK', TRUE);

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Dinesh HVAC', 'staff.dinesh@vit.ac.in', @pwd, 'STAFF', TRUE);
SET @staff4 = LAST_INSERT_ID();
INSERT INTO staff (staff_id, specialization, available) VALUES (@staff4, 'HVAC', TRUE);

-- ============ STUDENTS ============
INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Priya Sharma', 'priya.s@vitstudent.ac.in', @pwd, 'STUDENT', TRUE);
SET @s1 = LAST_INSERT_ID();
INSERT INTO students (student_id, room_number, hostel_block) VALUES (@s1, '101', 'A');

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Rahul Verma', 'rahul.v@vitstudent.ac.in', @pwd, 'STUDENT', TRUE);
SET @s2 = LAST_INSERT_ID();
INSERT INTO students (student_id, room_number, hostel_block) VALUES (@s2, '205', 'A');

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Anjali Patel', 'anjali.p@vitstudent.ac.in', @pwd, 'STUDENT', TRUE);
SET @s3 = LAST_INSERT_ID();
INSERT INTO students (student_id, room_number, hostel_block) VALUES (@s3, '302', 'B');

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Karthik Reddy', 'karthik.r@vitstudent.ac.in', @pwd, 'STUDENT', TRUE);
SET @s4 = LAST_INSERT_ID();
INSERT INTO students (student_id, room_number, hostel_block) VALUES (@s4, '410', 'B');

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Sneha Iyer', 'sneha.i@vitstudent.ac.in', @pwd, 'STUDENT', TRUE);
SET @s5 = LAST_INSERT_ID();
INSERT INTO students (student_id, room_number, hostel_block) VALUES (@s5, '108', 'C');

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Vijay Kumar', 'vijay.k@vitstudent.ac.in', @pwd, 'STUDENT', TRUE);
SET @s6 = LAST_INSERT_ID();
INSERT INTO students (student_id, room_number, hostel_block) VALUES (@s6, '215', 'C');

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Meera Nair', 'meera.n@vitstudent.ac.in', @pwd, 'STUDENT', TRUE);
SET @s7 = LAST_INSERT_ID();
INSERT INTO students (student_id, room_number, hostel_block) VALUES (@s7, '320', 'D');

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Arjun Das', 'arjun.d@vitstudent.ac.in', @pwd, 'STUDENT', TRUE);
SET @s8 = LAST_INSERT_ID();
INSERT INTO students (student_id, room_number, hostel_block) VALUES (@s8, '425', 'D');

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Deepika Raj', 'deepika.r@vitstudent.ac.in', @pwd, 'STUDENT', TRUE);
SET @s9 = LAST_INSERT_ID();
INSERT INTO students (student_id, room_number, hostel_block) VALUES (@s9, '112', 'A');

INSERT INTO users (name, email, password_hash, role, active) VALUES
    ('Suresh Menon', 'suresh.m@vitstudent.ac.in', @pwd, 'STUDENT', TRUE);
SET @s10 = LAST_INSERT_ID();
INSERT INTO students (student_id, room_number, hostel_block) VALUES (@s10, '225', 'B');

-- ============ COMPLAINTS (40 realistic complaints across all statuses) ============
-- Update the sequence counter
UPDATE complaint_sequence SET next_val = 41 WHERE year_val = 2026;

-- NEW complaints (8)
INSERT INTO complaints (complaint_id, student_id, category, description, priority, status, created_date, due_by) VALUES
    ('HCM-2026-000001', @s1, 'ELECTRICAL', 'Ceiling fan in room 101 is making grinding noise and wobbling dangerously', 2, 'NEW', NOW() - INTERVAL 2 HOUR, NOW() + INTERVAL 70 HOUR),
    ('HCM-2026-000002', @s2, 'PLUMBING', 'Water leakage from bathroom ceiling, water dripping on electrical wiring', 1, 'NEW', NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 23 HOUR),
    ('HCM-2026-000003', @s3, 'WIFI', 'Wi-Fi not connecting in B block 3rd floor since yesterday', 2, 'NEW', NOW() - INTERVAL 3 HOUR, NOW() + INTERVAL 69 HOUR),
    ('HCM-2026-000004', @s4, 'AC', 'AC unit leaking water onto the floor, creating puddle near power socket', 1, 'NEW', NOW() - INTERVAL 30 MINUTE, NOW() + INTERVAL 23 HOUR),
    ('HCM-2026-000005', @s5, 'CLEANING', 'Common washroom on 1st floor C block not cleaned for 3 days', 3, 'NEW', NOW() - INTERVAL 5 HOUR, NOW() + INTERVAL 163 HOUR),
    ('HCM-2026-000006', @s6, 'ELECTRICAL', 'Short circuit in room 215, sparks coming from switch board', 1, 'NEW', NOW() - INTERVAL 15 MINUTE, NOW() + INTERVAL 23 HOUR),
    ('HCM-2026-000007', @s7, 'CARPENTRY', 'Wardrobe door broken, hinge completely detached from frame', 3, 'NEW', NOW() - INTERVAL 8 HOUR, NOW() + INTERVAL 160 HOUR),
    ('HCM-2026-000008', @s8, 'PLUMBING', 'Tap in bathroom not closing properly, water running continuously', 2, 'NEW', NOW() - INTERVAL 4 HOUR, NOW() + INTERVAL 68 HOUR);

-- ASSIGNED complaints (7)
INSERT INTO complaints (complaint_id, student_id, staff_id, category, description, priority, status, created_date, due_by) VALUES
    ('HCM-2026-000009', @s9, @staff1, 'ELECTRICAL', 'Tubelight flickering in study room, causing headaches', 2, 'ASSIGNED', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 48 HOUR),
    ('HCM-2026-000010', @s10, @staff2, 'PLUMBING', 'Blocked drain in common bathroom B block 2nd floor', 2, 'ASSIGNED', NOW() - INTERVAL 12 HOUR, NOW() + INTERVAL 60 HOUR),
    ('HCM-2026-000011', @s1, @staff3, 'WIFI', 'Internet speed extremely slow in A block, cannot attend online classes', 1, 'ASSIGNED', NOW() - INTERVAL 6 HOUR, NOW() + INTERVAL 18 HOUR),
    ('HCM-2026-000012', @s2, @staff4, 'AC', 'AC not cooling at all, room temperature above 35 degrees', 1, 'ASSIGNED', NOW() - INTERVAL 4 HOUR, NOW() + INTERVAL 20 HOUR),
    ('HCM-2026-000013', @s3, @staff1, 'ELECTRICAL', 'Power socket in room 302 not working, unable to charge laptop', 2, 'ASSIGNED', NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 24 HOUR),
    ('HCM-2026-000014', @s4, @staff2, 'PLUMBING', 'Hot water not coming in geyser, possible heating element issue', 2, 'ASSIGNED', NOW() - INTERVAL 18 HOUR, NOW() + INTERVAL 54 HOUR),
    ('HCM-2026-000015', @s5, @staff3, 'WIFI', 'LAN port damaged in room 108, ethernet cable not connecting', 3, 'ASSIGNED', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 144 HOUR);

-- IN_PROGRESS complaints (8)
INSERT INTO complaints (complaint_id, student_id, staff_id, category, description, priority, status, created_date, due_by) VALUES
    ('HCM-2026-000016', @s6, @staff1, 'ELECTRICAL', 'Main switch board in corridor sparking when touched', 1, 'IN_PROGRESS', NOW() - INTERVAL 10 HOUR, NOW() + INTERVAL 14 HOUR),
    ('HCM-2026-000017', @s7, @staff2, 'PLUMBING', 'Flooding in D block ground floor bathroom due to pipe burst', 1, 'IN_PROGRESS', NOW() - INTERVAL 8 HOUR, NOW() + INTERVAL 16 HOUR),
    ('HCM-2026-000018', @s8, @staff4, 'AC', 'AC compressor making loud noise, disturbing sleep', 2, 'IN_PROGRESS', NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 24 HOUR),
    ('HCM-2026-000019', @s9, @staff3, 'WIFI', 'Router in A block 1st floor needs replacement', 2, 'IN_PROGRESS', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 1 HOUR),
    ('HCM-2026-000020', @s10, @staff1, 'ELECTRICAL', 'Emergency light not working in B block staircase', 1, 'IN_PROGRESS', NOW() - INTERVAL 20 HOUR, NOW() + INTERVAL 4 HOUR),
    ('HCM-2026-000021', @s1, @staff2, 'PLUMBING', 'Toilet flush mechanism broken in room 101 bathroom', 2, 'IN_PROGRESS', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 48 HOUR),
    ('HCM-2026-000022', @s2, @staff4, 'AC', 'AC remote not working, unable to control temperature', 3, 'IN_PROGRESS', NOW() - INTERVAL 4 DAY, NOW() + INTERVAL 96 HOUR),
    ('HCM-2026-000023', @s3, @staff1, 'ELECTRICAL', 'Fan regulator not working, fan runs only at full speed', 2, 'IN_PROGRESS', NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 24 HOUR);

-- RESOLVED complaints (8)
INSERT INTO complaints (complaint_id, student_id, staff_id, category, description, priority, status, created_date, resolved_date, due_by) VALUES
    ('HCM-2026-000024', @s4, @staff2, 'PLUMBING', 'Water purifier filter needs replacement in B block common area', 2, 'RESOLVED', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('HCM-2026-000025', @s5, @staff1, 'ELECTRICAL', 'Inverter battery backup not working during power cuts', 1, 'RESOLVED', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 2 DAY),
    ('HCM-2026-000026', @s6, @staff3, 'WIFI', 'Wi-Fi password changed and not updated in C block notice board', 3, 'RESOLVED', NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 5 DAY, NOW()),
    ('HCM-2026-000027', @s7, @staff4, 'AC', 'AC gas needs refilling, not cooling properly', 2, 'RESOLVED', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('HCM-2026-000028', @s8, @staff2, 'PLUMBING', 'Shower head broken, needs replacement', 3, 'RESOLVED', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 3 DAY),
    ('HCM-2026-000029', @s9, @staff1, 'ELECTRICAL', 'Exhaust fan in bathroom not working', 3, 'RESOLVED', NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 1 DAY),
    ('HCM-2026-000030', @s10, @staff3, 'WIFI', 'Ethernet cable damaged in room 225', 2, 'RESOLVED', NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
    ('HCM-2026-000031', @s1, @staff4, 'AC', 'AC filter dirty, causing allergic reactions', 1, 'RESOLVED', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

-- CLOSED complaints (7)
INSERT INTO complaints (complaint_id, student_id, staff_id, category, description, priority, status, created_date, resolved_date, due_by) VALUES
    ('HCM-2026-000032', @s2, @staff1, 'ELECTRICAL', 'Night lamp socket not working in room 205', 3, 'CLOSED', NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 8 DAY),
    ('HCM-2026-000033', @s3, @staff2, 'PLUMBING', 'Wash basin tap leaking in room 302', 2, 'CLOSED', NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 17 DAY, NOW() - INTERVAL 17 DAY),
    ('HCM-2026-000034', @s4, @staff3, 'WIFI', 'Cannot connect to VIT Wi-Fi network from room 410', 2, 'CLOSED', NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 22 DAY),
    ('HCM-2026-000035', @s5, @staff4, 'AC', 'AC making water dripping sound', 3, 'CLOSED', NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 23 DAY),
    ('HCM-2026-000036', @s6, @staff1, 'ELECTRICAL', 'Power strip burned out in room 215', 1, 'CLOSED', NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 17 DAY, NOW() - INTERVAL 17 DAY),
    ('HCM-2026-000037', @s7, @staff2, 'CLEANING', 'Pest control needed in D block rooms', 2, 'CLOSED', NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 19 DAY),
    ('HCM-2026-000038', @s8, @staff4, 'CARPENTRY', 'Study table drawer jammed, cannot open', 3, 'CLOSED', NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 24 DAY, NOW() - INTERVAL 21 DAY);

-- REOPENED complaints (2)
INSERT INTO complaints (complaint_id, student_id, staff_id, category, description, priority, status, created_date, due_by) VALUES
    ('HCM-2026-000039', @s9, @staff1, 'ELECTRICAL', 'Fan fixed but started wobbling again after 2 days', 2, 'REOPENED', NOW() - INTERVAL 6 DAY, NOW() + INTERVAL 18 HOUR),
    ('HCM-2026-000040', @s10, @staff2, 'PLUMBING', 'Pipe repair done but leaking again from a different joint', 1, 'REOPENED', NOW() - INTERVAL 4 DAY, NOW() + INTERVAL 20 HOUR);

-- ============ COMPLAINT HISTORY for some complaints ============
INSERT INTO complaint_history (complaint_id, old_status, new_status, changed_by, note, changed_at) VALUES
    ('HCM-2026-000009', 'NEW', 'ASSIGNED', @admin_id, 'Assigned to Arun for electrical inspection', NOW() - INTERVAL 22 HOUR),
    ('HCM-2026-000016', 'NEW', 'ASSIGNED', @admin_id, 'Urgent: sparking switch board, assigned immediately', NOW() - INTERVAL 9 HOUR),
    ('HCM-2026-000016', 'ASSIGNED', 'IN_PROGRESS', @staff1, 'Started inspection, need to cut power first', NOW() - INTERVAL 8 HOUR),
    ('HCM-2026-000024', 'NEW', 'ASSIGNED', @admin_id, 'Routine maintenance assignment', NOW() - INTERVAL 4 DAY),
    ('HCM-2026-000024', 'ASSIGNED', 'IN_PROGRESS', @staff2, 'Ordered replacement filter', NOW() - INTERVAL 3 DAY),
    ('HCM-2026-000024', 'IN_PROGRESS', 'RESOLVED', @staff2, 'Filter replaced and tested', NOW() - INTERVAL 2 DAY),
    ('HCM-2026-000032', 'NEW', 'ASSIGNED', @admin_id, 'Assigned', NOW() - INTERVAL 14 DAY),
    ('HCM-2026-000032', 'ASSIGNED', 'IN_PROGRESS', @staff1, 'Checking wiring', NOW() - INTERVAL 13 DAY),
    ('HCM-2026-000032', 'IN_PROGRESS', 'RESOLVED', @staff1, 'Socket replaced', NOW() - INTERVAL 12 DAY),
    ('HCM-2026-000032', 'RESOLVED', 'CLOSED', @s2, 'Working fine now, thank you', NOW() - INTERVAL 11 DAY),
    ('HCM-2026-000039', 'IN_PROGRESS', 'RESOLVED', @staff1, 'Fan tightened and balanced', NOW() - INTERVAL 3 DAY),
    ('HCM-2026-000039', 'RESOLVED', 'REOPENED', @s9, 'Fan started wobbling again after 2 days', NOW() - INTERVAL 1 DAY);

-- ============ FEEDBACK for closed/resolved complaints ============
INSERT INTO feedback (complaint_id, rating, comment, created_at) VALUES
    ('HCM-2026-000032', 4, 'Quick fix, but took a while to get assigned', NOW() - INTERVAL 11 DAY),
    ('HCM-2026-000033', 5, 'Excellent work, no more leaking!', NOW() - INTERVAL 16 DAY),
    ('HCM-2026-000034', 3, 'Wi-Fi works now but speed could be better', NOW() - INTERVAL 21 DAY),
    ('HCM-2026-000035', 4, 'AC is quiet now, good service', NOW() - INTERVAL 25 DAY),
    ('HCM-2026-000036', 5, 'Very fast response for emergency, grateful!', NOW() - INTERVAL 16 DAY),
    ('HCM-2026-000037', 4, 'Pest control done thoroughly', NOW() - INTERVAL 18 DAY),
    ('HCM-2026-000038', 3, 'Drawer works but alignment is slightly off', NOW() - INTERVAL 23 DAY);

-- ============ COMMENTS ============
INSERT INTO complaint_comments (complaint_id, author_id, message, created_at) VALUES
    ('HCM-2026-000002', @s2, 'This is urgent, water is reaching the electrical wiring!', NOW() - INTERVAL 50 MINUTE),
    ('HCM-2026-000016', @staff1, 'I have shut off the power to the corridor. Will replace the switch board today.', NOW() - INTERVAL 7 HOUR),
    ('HCM-2026-000016', @admin_id, 'Please prioritize this, safety issue.', NOW() - INTERVAL 8 HOUR),
    ('HCM-2026-000019', @staff3, 'New router ordered, expected delivery tomorrow', NOW() - INTERVAL 1 DAY),
    ('HCM-2026-000019', @s9, 'Thank you, please update when installed', NOW() - INTERVAL 22 HOUR),
    ('HCM-2026-000039', @s9, 'The fan is wobbling again, please check the blade balance this time', NOW() - INTERVAL 23 HOUR);

-- ============ NOTIFICATIONS ============
INSERT INTO notifications (user_id, message, read_flag, created_at) VALUES
    (@s1, 'Your complaint HCM-2026-000001 has been registered successfully.', TRUE, NOW() - INTERVAL 2 HOUR),
    (@s2, 'Your complaint HCM-2026-000002 has been registered. Priority: HIGH', FALSE, NOW() - INTERVAL 1 HOUR),
    (@staff1, 'New task assigned: HCM-2026-000009 - Tubelight flickering in study room', TRUE, NOW() - INTERVAL 22 HOUR),
    (@staff1, 'New task assigned: HCM-2026-000016 - Main switch board sparking', FALSE, NOW() - INTERVAL 9 HOUR),
    (@s4, 'Your complaint HCM-2026-000024 has been resolved. Please review and provide feedback.', TRUE, NOW() - INTERVAL 2 DAY),
    (@admin_id, '3 complaints are overdue and need attention', FALSE, NOW() - INTERVAL 1 HOUR),
    (@s9, 'Your reopened complaint HCM-2026-000039 is being reassigned.', FALSE, NOW() - INTERVAL 1 DAY);
