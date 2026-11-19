-- =====================================================================
-- JobTrack - 02_security.sql  (chạy SAU 01_schema.sql)
--
-- Supabase công khai bảng trong schema "public" qua REST API (dùng anon key).
-- JobTrack KHÔNG dùng API đó → chặn hết:
--   ① Bật Row Level Security (RLS) cho mọi bảng
--   ② Thu hồi quyền của anon / authenticated (vai trò dùng cho REST API)
--   ③ Tạo user riêng cho app (jobtrack_app) – quyền tối thiểu, chỉ đọc/ghi dữ liệu
--
-- !! Đổi 'CHANGE_ME' thành mật khẩu của bạn trước khi chạy. KHÔNG commit mật khẩu thật.
-- =====================================================================

-- ③ user cho app
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jobtrack_app') THEN
        CREATE ROLE jobtrack_app WITH LOGIN PASSWORD 'CHANGE_ME';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO jobtrack_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jobtrack_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jobtrack_app;

-- ② REST API của Supabase không được đọc/ghi
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated;

-- ① RLS: bật cho mọi bảng, chỉ cho phép jobtrack_app
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['users', 'companies', 'applications', 'interviews', 'tasks',
                             'ai_results', 'part_time_shifts', 'spring_session', 'spring_session_attributes']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS app_full_access ON %I', t);
        EXECUTE format('CREATE POLICY app_full_access ON %I FOR ALL TO jobtrack_app USING (true) WITH CHECK (true)', t);
    END LOOP;
END
$$;
