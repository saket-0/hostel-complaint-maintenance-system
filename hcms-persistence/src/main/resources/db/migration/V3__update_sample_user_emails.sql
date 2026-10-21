-- V3: Standardize sample user emails to firstname.lastname format
-- All sample passwords remain BCrypt hash of 'password123'
SET @pwd = '$2a$12$7f8SSpbLSfJGaXvNRMODLuu6jSoCH9QJvkHWSVMfxema4J5Bp3H66';

-- ============ UPDATE STUDENTS ============
-- Standardize existing students to firstname.lastname@vitstudent.ac.in
UPDATE users SET email = 'priya.sharma@vitstudent.ac.in' WHERE email = 'priya.s@vitstudent.ac.in';
UPDATE users SET email = 'rahul.verma@vitstudent.ac.in' WHERE email = 'rahul.v@vitstudent.ac.in';
UPDATE users SET email = 'anjali.patel@vitstudent.ac.in' WHERE email = 'anjali.p@vitstudent.ac.in';
UPDATE users SET email = 'karthik.reddy@vitstudent.ac.in' WHERE email = 'karthik.r@vitstudent.ac.in';
UPDATE users SET email = 'sneha.iyer@vitstudent.ac.in' WHERE email = 'sneha.i@vitstudent.ac.in';
UPDATE users SET email = 'vijay.kumar@vitstudent.ac.in' WHERE email = 'vijay.k@vitstudent.ac.in';
UPDATE users SET email = 'meera.nair@vitstudent.ac.in' WHERE email = 'meera.n@vitstudent.ac.in';
UPDATE users SET email = 'arjun.das@vitstudent.ac.in' WHERE email = 'arjun.d@vitstudent.ac.in';
UPDATE users SET email = 'deepika.raj@vitstudent.ac.in' WHERE email = 'deepika.r@vitstudent.ac.in';
UPDATE users SET email = 'suresh.menon@vitstudent.ac.in' WHERE email = 'suresh.m@vitstudent.ac.in';

-- ============ UPDATE STAFF ============
-- Standardize staff names and emails to firstname.lastname@vit.ac.in
UPDATE users SET name = 'Arun Kumar', email = 'arun.kumar@vit.ac.in' WHERE email = 'staff.arun@vit.ac.in';
UPDATE users SET name = 'Bala Murugan', email = 'bala.murugan@vit.ac.in' WHERE email = 'staff.bala@vit.ac.in';
UPDATE users SET name = 'Chandra Sekhar', email = 'chandra.sekhar@vit.ac.in' WHERE email = 'staff.chandra@vit.ac.in';
UPDATE users SET name = 'Dinesh Karthik', email = 'dinesh.karthik@vit.ac.in' WHERE email = 'staff.dinesh@vit.ac.in';

-- ============ ADDITIONAL SAMPLE STUDENTS ============
-- Ensure demo students referenced in documentation are directly accessible
INSERT INTO users (name, email, password_hash, role, active)
SELECT 'Rohan Gupta', 'rohan.gupta@vitstudent.ac.in', @pwd, 'STUDENT', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'rohan.gupta@vitstudent.ac.in');

SET @rohan_id = (SELECT user_id FROM users WHERE email = 'rohan.gupta@vitstudent.ac.in');
INSERT INTO students (student_id, room_number, hostel_block)
SELECT @rohan_id, '301', 'A'
WHERE @rohan_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM students WHERE student_id = @rohan_id);
