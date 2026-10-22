-- V4: Add registration number, phone, and password reset requirement for students
ALTER TABLE users ADD COLUMN force_password_change BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE students ADD COLUMN registration_number VARCHAR(30) NULL;
ALTER TABLE students ADD COLUMN phone VARCHAR(20) NULL;

-- Backfill registration numbers for existing seed students
UPDATE students SET registration_number = CONCAT('21BCE', LPAD(student_id, 4, '0')) WHERE registration_number IS NULL;

-- Enforce unique registration number constraint
ALTER TABLE students ADD CONSTRAINT uq_students_reg_no UNIQUE (registration_number);

-- User management audit history
CREATE TABLE IF NOT EXISTS user_history (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    action     VARCHAR(50) NOT NULL,
    changed_by BIGINT NOT NULL,
    note       TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_history_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_history_actor FOREIGN KEY (changed_by) REFERENCES users(user_id),
    INDEX idx_user_history_user (user_id),
    INDEX idx_user_history_changed (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
