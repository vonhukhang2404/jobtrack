-- =====================================================================
-- JobTrack - 03_seed.sql : dữ liệu mẫu (PostgreSQL / Supabase)
-- Chạy SAU 01_schema.sql và 02_security.sql. Chạy lại được nhiều lần.
-- Tài khoản demo:  demo@jobtrack.local / password123
-- (Tên công ty là tên giả)
-- =====================================================================

TRUNCATE TABLE part_time_shifts, ai_results, tasks, interviews, applications, companies, users
    RESTART IDENTITY CASCADE;

-- 1. users (password_hash = BCrypt của "password123")
INSERT INTO users (id, name, email, password_hash) VALUES
(1, 'Demo User', 'demo@jobtrack.local', '$2a$10$6KrDFGEPP4QLaXtLLyExA.3AmEQHvYZnCGUBtfuza4n7Ty1t5scP2');

-- 2. companies
INSERT INTO companies (id, user_id, name, industry, url, memo) VALUES
(1, 1, 'サクラシステム株式会社', 'IT',   'https://example.com/sakura', '大阪本社。Java案件が多い'),
(2, 1, 'ナニワソフト株式会社',   'IT',   'https://example.com/naniwa', 'ベトナム拠点あり'),
(3, 1, 'ミライデータ株式会社',   'IT',   'https://example.com/mirai',  'AI・データ分析'),
(4, 1, 'カンサイ物流株式会社',   '物流', 'https://example.com/kansai', '社内SE募集'),
(5, 1, 'ウメダ銀行',             '金融', 'https://example.com/umeda',  NULL);

-- 3. applications  (E'...\n...' = chuỗi có xuống dòng trong PostgreSQL)
INSERT INTO applications (id, company_id, position, status, applied_at, jd_text) VALUES
(1, 1, 'インターン（夏）',         'REJECTED',         '2026-06-10', NULL),
(2, 1, 'Javaエンジニア（本選考）', 'SECOND_INTERVIEW', '2026-09-20',
   E'【業務内容】Spring Bootを用いた業務系Webシステムの開発\n【必須】Java、SQLの基礎知識\n【歓迎】Git、AWSの利用経験\n【選考】書類選考→一次面接→二次面接→最終面接'),
(3, 2, 'システムエンジニア',       'FIRST_INTERVIEW',  '2026-09-28', NULL),
(4, 3, 'AIエンジニア',             'DOCUMENT',         '2026-10-05', NULL),
(5, 4, '社内SE',                   'ENTRY',            '2026-10-08', NULL),
(6, 5, 'ITエンジニア',             'NOT_APPLIED',      NULL,         NULL);

-- 4. interviews
INSERT INTO interviews (application_id, round, scheduled_at, format, result, notes) VALUES
(2, 1, '2026-10-06 14:00:00', 'ONLINE',  'PASSED',  '志望動機を深掘りされた。OOPの説明は良かった'),
(2, 2, '2026-10-20 10:00:00', 'OFFLINE', 'PENDING', NULL),
(3, 1, '2026-10-15 13:00:00', 'ONLINE',  'PENDING', NULL),
(4, 1, '2026-12-03 15:00:00', 'ONLINE',  'PENDING', NULL);       -- phỏng vấn sắp tới (cho dashboard)

-- 5. tasks
INSERT INTO tasks (application_id, title, due_date, done) VALUES
(2, 'ES提出',                   '2026-09-20', TRUE),
(2, '適性検査（SPI）受検',      '2026-09-30', TRUE),
(2, '二次面接の逆質問を準備',   '2026-10-19', FALSE),
(3, '一次面接の自己紹介を練習', '2026-10-14', FALSE),
(4, 'ポートフォリオURL提出',    '2026-10-20', FALSE),
(6, 'エントリーシート作成',     '2026-10-31', FALSE);

-- 6. ai_results
INSERT INTO ai_results (application_id, type, input_text, output_text) VALUES
(2, 'JD_SUMMARY', '（求人票の本文）',
   E'必要スキル：Java、SQL、Spring Boot\n準備ポイント：DB設計の経験、チーム開発でのGit利用を説明できるようにする');

-- 7. part_time_shifts
--    ① 10/20 09:00〜13:00 trùng 二次面接 (10/20 10:00)  ② tuần 11/02: 29h (vượt 28h)  ③ tuần 11/09: 2h
INSERT INTO part_time_shifts (user_id, workplace, start_at, end_at, memo) VALUES
(1, 'ローソン 今里店', '2026-10-20 09:00:00', '2026-10-20 13:00:00', '面接と重複（テスト用）'),
(1, 'ローソン 今里店', '2026-11-02 17:00:00', '2026-11-02 22:00:00', NULL),
(1, 'ローソン 今里店', '2026-11-03 17:00:00', '2026-11-03 22:00:00', NULL),
(1, 'ローソン 今里店', '2026-11-04 17:00:00', '2026-11-04 22:00:00', NULL),
(1, 'ローソン 今里店', '2026-11-05 17:00:00', '2026-11-05 22:00:00', NULL),
(1, 'ローソン 今里店', '2026-11-06 17:00:00', '2026-11-06 22:00:00', NULL),
(1, 'ローソン 今里店', '2026-11-07 12:00:00', '2026-11-07 16:00:00', '週28時間を超える（テスト用）'),
(1, 'ローソン 今里店', '2026-11-09 12:00:00', '2026-11-09 14:00:00', NULL);

-- Đã chèn id cố định (1, 2, ...) → chỉnh lại bộ đếm id để lần INSERT sau không bị trùng
SELECT setval(pg_get_serial_sequence('users', 'id'),        (SELECT MAX(id) FROM users));
SELECT setval(pg_get_serial_sequence('companies', 'id'),    (SELECT MAX(id) FROM companies));
SELECT setval(pg_get_serial_sequence('applications', 'id'), (SELECT MAX(id) FROM applications));

-- Kiểm tra số dòng
SELECT 'users' AS tbl, COUNT(*) AS cnt FROM users
UNION ALL SELECT 'companies',        COUNT(*) FROM companies
UNION ALL SELECT 'applications',     COUNT(*) FROM applications
UNION ALL SELECT 'interviews',       COUNT(*) FROM interviews
UNION ALL SELECT 'tasks',            COUNT(*) FROM tasks
UNION ALL SELECT 'ai_results',       COUNT(*) FROM ai_results
UNION ALL SELECT 'part_time_shifts', COUNT(*) FROM part_time_shifts;
