-- =====================================================================
-- Migration 001: thêm bảng part_time_shifts (ca làm thêm)
-- Chạy trên DB ĐANG CÓ DỮ LIỆU (không xoá dữ liệu cũ):
--   mysql -u root -p jobtrack < database/migrations/001_create_part_time_shifts.sql
-- (Người cài mới chỉ cần chạy schema.sql, bảng này đã có trong đó)
-- =====================================================================
USE jobtrack;

CREATE TABLE IF NOT EXISTS part_time_shifts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    workplace   VARCHAR(100) NOT NULL,                    -- nơi làm: ローソン 今里店...
    start_at    DATETIME     NOT NULL,
    end_at      DATETIME     NOT NULL,
    memo        TEXT         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_shifts_user_start (user_id, start_at),        -- "ca của user, theo thời gian"
    CONSTRAINT fk_shifts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_shifts_time CHECK (end_at > start_at)   -- giờ kết thúc phải sau giờ bắt đầu
) ENGINE = InnoDB;
