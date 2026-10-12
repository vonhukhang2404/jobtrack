-- =====================================================================
-- JobTrack - create_app_user.sql
-- Tạo user riêng cho ứng dụng Spring Boot (không dùng root).
-- Chạy bằng root, MỘT LẦN, SAU schema.sql:
--   mysql -u root -p < create_app_user.sql
--
-- !! Trước khi chạy: đổi 'CHANGE_ME' thành mật khẩu của bạn.
-- !! Không commit mật khẩu thật lên Git (đổi lại thành CHANGE_ME trước khi commit).
-- =====================================================================

DROP USER IF EXISTS 'jobtrack_app'@'localhost';

CREATE USER 'jobtrack_app'@'localhost' IDENTIFIED BY 'CHANGE_ME';

-- Quyền tối thiểu: chỉ đọc/ghi dữ liệu trong database jobtrack.
GRANT SELECT, INSERT, UPDATE, DELETE ON jobtrack.* TO 'jobtrack_app'@'localhost';

FLUSH PRIVILEGES;

SHOW GRANTS FOR 'jobtrack_app'@'localhost';
