-- ============================================================
-- Migration Script: schema.sql
-- Database: MySQL 8.x
-- Hệ thống Quản lý Chấm công (Attendance) & Nghỉ phép (Leave)
-- ============================================================

-- 1. Bảng users (Nhân viên / Người dùng)
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `full_name` VARCHAR(100) NOT NULL,
    `email` VARCHAR(150) NOT NULL UNIQUE,
    `department` VARCHAR(100) NULL,
    `role` VARCHAR(50) NOT NULL,
    CONSTRAINT `chk_user_role` CHECK (`role` IN ('EMPLOYEE', 'MANAGER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Bảng attendances (Chấm công hàng ngày)
CREATE TABLE IF NOT EXISTS `attendances` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `work_date` DATE NOT NULL,
    `check_in_time` DATETIME NULL,
    `check_out_time` DATETIME NULL,
    `work_hours` DOUBLE NULL,
    `status` VARCHAR(30) NOT NULL,
    `notes` TEXT NULL,
    CONSTRAINT `fk_attendance_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `uk_attendance_user_date` UNIQUE (`user_id`, `work_date`),
    CONSTRAINT `chk_attendance_status` CHECK (`status` IN ('PRESENT', 'LATE', 'EARLY_LEAVE', 'ABSENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Bảng leave_requests (Đơn yêu cầu nghỉ phép)
CREATE TABLE IF NOT EXISTS `leave_requests` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `leave_type` VARCHAR(30) NOT NULL,
    `start_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `total_days` DOUBLE NOT NULL,
    `reason` TEXT NULL,
    `attachment_url` VARCHAR(500) NULL,
    `status` VARCHAR(30) NOT NULL,
    `approver_id` BIGINT NULL,
    `rejection_reason` TEXT NULL,
    CONSTRAINT `fk_leave_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_leave_approver` FOREIGN KEY (`approver_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `chk_leave_type` CHECK (`leave_type` IN ('ANNUAL', 'UNPAID', 'SICK')),
    CONSTRAINT `chk_leave_status` CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Bảng leave_balances (Quỹ ngày phép theo năm của nhân viên)
CREATE TABLE IF NOT EXISTS `leave_balances` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `year` INT NOT NULL,
    `total_days` DOUBLE NOT NULL DEFAULT 0.0,
    `used_days` DOUBLE NOT NULL DEFAULT 0.0,
    `remaining_days` DOUBLE NOT NULL DEFAULT 0.0,
    CONSTRAINT `fk_balance_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `uk_leave_balance_user_year` UNIQUE (`user_id`, `year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tối ưu hóa truy vấn bằng Indexes
CREATE INDEX `idx_attendance_work_date` ON `attendances` (`work_date`);
CREATE INDEX `idx_leave_request_status` ON `leave_requests` (`status`);
CREATE INDEX `idx_leave_request_dates` ON `leave_requests` (`user_id`, `start_date`, `end_date`);

