-- =====================================================================
-- JobTrack - schema.sql  (MySQL 8.0.16+)
-- Chạy:  mysql -u root -p < schema.sql
-- 7 bảng, tạo theo thứ tự phụ thuộc FK:
--   users → companies → applications → interviews / tasks / ai_results
--   users → part_time_shifts
-- =====================================================================

-- Chỉ xoá database jobtrack (không đụng tới bookshare, laravel10, studb...)
DROP DATABASE IF EXISTS jobtrack;
CREATE DATABASE jobtrack
    DEFAULT CHARACTER SET utf8mb4          -- lưu được tiếng Nhật, emoji
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE jobtrack;

-- ---------------------------------------------------------------------
-- 1. users : tài khoản đăng nhập
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(50)  NOT NULL,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,                 -- BCrypt (60 ký tự)
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)                     -- email dùng để đăng nhập, không trùng
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 2. companies : công ty mà user quan tâm / ứng tuyển
--    Mỗi user có danh sách công ty riêng (user_id).
-- ---------------------------------------------------------------------
CREATE TABLE companies (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    industry    VARCHAR(50)  NULL,                        -- ngành: IT, 製造, 金融...
    url         VARCHAR(255) NULL,
    memo        TEXT         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companies_user_name (user_id, name),    -- 1 user không đăng ký trùng tên công ty
    -- Xoá user → xoá luôn công ty của user đó
    CONSTRAINT fk_companies_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 3. applications : 1 lần ứng tuyển (1 vị trí / 1 đợt của 1 công ty)
--    Không có user_id: chủ sở hữu xác định qua company → user (chuẩn hoá 3NF).
-- ---------------------------------------------------------------------
CREATE TABLE applications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    company_id  BIGINT       NOT NULL,
    position    VARCHAR(100) NOT NULL,                    -- vị trí: Javaエンジニア, インターン...
    status      VARCHAR(20)  NOT NULL DEFAULT 'NOT_APPLIED',
    applied_at  DATE         NULL,                        -- NULL = chưa nộp
    jd_text     TEXT         NULL,                        -- nội dung 求人票 (dán vào để AI phân tích)
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_applications_company_status (company_id, status),  -- lọc theo trạng thái
    CONSTRAINT fk_applications_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT ck_applications_status CHECK (status IN (
        'NOT_APPLIED',       -- 未応募
        'ENTRY',             -- エントリー
        'DOCUMENT',          -- 書類選考
        'FIRST_INTERVIEW',   -- 一次面接
        'SECOND_INTERVIEW',  -- 二次面接
        'FINAL_INTERVIEW',   -- 最終面接
        'OFFER',             -- 内定
        'REJECTED',          -- 不合格
        'DECLINED'))         -- 辞退
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 4. interviews : từng vòng phỏng vấn của 1 lần ứng tuyển
-- ---------------------------------------------------------------------
CREATE TABLE interviews (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    application_id  BIGINT      NOT NULL,
    round           INT         NOT NULL,                 -- vòng 1, 2, 3...
    scheduled_at    DATETIME    NOT NULL,
    format          VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    result          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes           TEXT        NULL,                     -- ghi chú / rút kinh nghiệm sau phỏng vấn
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_interviews_app_round (application_id, round),  -- 1 vòng chỉ có 1 dòng
    KEY idx_interviews_scheduled (scheduled_at),                  -- "lịch phỏng vấn sắp tới"
    CONSTRAINT fk_interviews_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT ck_interviews_round  CHECK (round >= 1),
    CONSTRAINT ck_interviews_format CHECK (format IN ('ONLINE', 'OFFLINE')),
    CONSTRAINT ck_interviews_result CHECK (result IN ('PENDING', 'PASSED', 'FAILED'))
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 5. tasks : deadline / ToDo (ES提出, SPI, chuẩn bị phỏng vấn...)
--    Quá hạn không lưu thành cột, mà tính khi hiển thị:
--    done = FALSE AND due_date < CURDATE()
-- ---------------------------------------------------------------------
CREATE TABLE tasks (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    application_id  BIGINT       NOT NULL,
    title           VARCHAR(100) NOT NULL,
    due_date        DATE         NOT NULL,
    done            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tasks_application (application_id),
    KEY idx_tasks_done_due (done, due_date),              -- "việc chưa xong, sắp đến hạn"
    CONSTRAINT fk_tasks_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 6. ai_results : kết quả AI trả về (lưu lại để không gọi API lại)
-- ---------------------------------------------------------------------
CREATE TABLE ai_results (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    application_id  BIGINT      NOT NULL,
    type            VARCHAR(20) NOT NULL,
    input_text      TEXT        NOT NULL,                 -- nội dung gửi cho AI
    output_text     TEXT        NOT NULL,                 -- AI trả về (text hoặc JSON)
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_results_app_type (application_id, type),   -- lấy kết quả mới nhất theo loại
    CONSTRAINT fk_ai_results_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_results_type CHECK (type IN (
        'JD_SUMMARY',   -- tóm tắt 求人票
        'QUESTIONS',    -- câu hỏi phỏng vấn dự đoán
        'AUTO_FILL',    -- trích tên công ty, deadline... để tự điền form
        'ES_REVIEW'))   -- góp ý ES (chức năng thêm)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 7. part_time_shifts : ca làm thêm (アルバイト), gắn trực tiếp với user
--    Dùng để cảnh báo trùng giờ với phỏng vấn và tổng giờ/tuần > 28h
--    (thêm ở tuần 4: database/migrations/001_create_part_time_shifts.sql)
-- ---------------------------------------------------------------------
CREATE TABLE part_time_shifts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    workplace   VARCHAR(100) NOT NULL,
    start_at    DATETIME     NOT NULL,
    end_at      DATETIME     NOT NULL,
    memo        TEXT         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_shifts_user_start (user_id, start_at),
    CONSTRAINT fk_shifts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_shifts_time CHECK (end_at > start_at)
) ENGINE = InnoDB;
