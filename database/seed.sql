-- =====================================================================
-- JobTrack - seed.sql : dữ liệu mẫu để test / demo
-- Chạy SAU schema.sql:  mysql -u root -p --default-character-set=utf8mb4 jobtrack < seed.sql
-- Chạy lại nhiều lần được (dữ liệu cũ sẽ bị xoá trước khi nạp)
-- Tài khoản demo:  demo@jobtrack.local / password123
-- (Tên công ty là tên giả, không phải công ty thật)
-- =====================================================================
USE jobtrack;
SET NAMES utf8mb4;   -- gửi dữ liệu dạng UTF-8 (tránh lỗi 1366 với tiếng Nhật trên Windows)

-- Xoá dữ liệu cũ để chạy lại file này nhiều lần không bị lỗi trùng khoá
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE ai_results;
TRUNCATE TABLE tasks;
TRUNCATE TABLE interviews;
TRUNCATE TABLE applications;
TRUNCATE TABLE companies;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

-- 1. users (password_hash = BCrypt của "password123")
INSERT INTO users (id, name, email, password_hash) VALUES
(1, 'Demo User', 'demo@jobtrack.local', '$2a$10$6KrDFGEPP4QLaXtLLyExA.3AmEQHvYZnCGUBtfuza4n7Ty1t5scP2');

-- 2. companies
INSERT INTO companies (id, user_id, name, industry, url, memo) VALUES
(1, 1, 'サクラシステム株式会社',     'IT',     'https://example.com/sakura',   '大阪本社。Java案件が多い'),
(2, 1, 'ナニワソフト株式会社',       'IT',     'https://example.com/naniwa',   'ベトナム拠点あり'),
(3, 1, 'ミライデータ株式会社',       'IT',     'https://example.com/mirai',    'AI・データ分析'),
(4, 1, 'カンサイ物流株式会社',       '物流',   'https://example.com/kansai',   '社内SE募集'),
(5, 1, 'ウメダ銀行',                 '金融',   'https://example.com/umeda',    NULL);

-- 3. applications
INSERT INTO applications (id, company_id, position, status, applied_at, jd_text) VALUES
(1, 1, 'インターン（夏）',           'REJECTED',         '2026-06-10', NULL),
(2, 1, 'Javaエンジニア（本選考）',   'SECOND_INTERVIEW', '2026-09-20',
   '【業務内容】Spring Bootを用いた業務系Webシステムの開発\n【必須】Java、SQLの基礎知識\n【歓迎】Git、AWSの利用経験\n【選考】書類選考→一次面接→二次面接→最終面接'),
(3, 2, 'システムエンジニア',         'FIRST_INTERVIEW',  '2026-09-28', NULL),
(4, 3, 'AIエンジニア',               'DOCUMENT',         '2026-10-05', NULL),
(5, 4, '社内SE',                     'ENTRY',            '2026-10-08', NULL),
(6, 5, 'ITエンジニア',               'NOT_APPLIED',      NULL,         NULL);

-- 4. interviews
INSERT INTO interviews (application_id, round, scheduled_at, format, result, notes) VALUES
(2, 1, '2026-10-06 14:00:00', 'ONLINE',  'PASSED',  '志望動機を深掘りされた。OOPの説明は良かった'),
(2, 2, '2026-10-20 10:00:00', 'OFFLINE', 'PENDING', NULL),
(3, 1, '2026-10-15 13:00:00', 'ONLINE',  'PENDING', NULL);

-- 5. tasks
INSERT INTO tasks (application_id, title, due_date, done) VALUES
(2, 'ES提出',                     '2026-09-20', TRUE),
(2, '適性検査（SPI）受検',        '2026-09-30', TRUE),
(2, '二次面接の逆質問を準備',     '2026-10-19', FALSE),
(3, '一次面接の自己紹介を練習',   '2026-10-14', FALSE),
(4, 'ポートフォリオURL提出',      '2026-10-20', FALSE),
(6, 'エントリーシート作成',       '2026-10-31', FALSE);

-- 6. ai_results
INSERT INTO ai_results (application_id, type, input_text, output_text) VALUES
(2, 'JD_SUMMARY',
   '（求人票の本文）',
   '必要スキル：Java、SQL、Spring Boot\n準備ポイント：DB設計の経験、チーム開発でのGit利用を説明できるようにする');

-- Kiểm tra số dòng
SELECT 'users' AS tbl, COUNT(*) AS cnt FROM users
UNION ALL SELECT 'companies',    COUNT(*) FROM companies
UNION ALL SELECT 'applications', COUNT(*) FROM applications
UNION ALL SELECT 'interviews',   COUNT(*) FROM interviews
UNION ALL SELECT 'tasks',        COUNT(*) FROM tasks
UNION ALL SELECT 'ai_results',   COUNT(*) FROM ai_results;
