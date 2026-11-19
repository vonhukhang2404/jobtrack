# Tuần 7 – Deploy: PostgreSQL（Supabase）+ Vercel（11/16〜）

## Quyết định

| | Trước | Sau |
|---|---|---|
| DB | MySQL（local） | **PostgreSQL（Supabase）**: `jobtrack-dev`（khi phát triển）/ `jobtrack-prod`（bản deploy） |
| Chạy app | IntelliJ（local） | IntelliJ（dev）/ **Vercel**（Container Runtime, Dockerfile） |
| Session đăng nhập | Trong bộ nhớ | **Trong DB**（Spring Session JDBC）→ Vercel tắt app sau 5 phút không có truy cập vẫn không bị đăng xuất |

Lý do: miễn phí, không cần cài DB trên máy. Rủi ro: Vercel Container Runtime đang **Beta**; app ngủ → lần đầu chậm; Supabase gói miễn phí tạm dừng sau 1 tuần không dùng.

## Đã làm（11/16 – Claude）

| File | Nội dung |
|---|---|
| `database/postgres/01_schema.sql` | 7 bảng + `spring_session`（IDENTITY, TIMESTAMP, trigger `set_updated_at`） |
| `database/postgres/02_security.sql` | User `jobtrack_app`（quyền tối thiểu）, bật **RLS**, chặn REST API của Supabase（anon / authenticated） |
| `database/postgres/03_seed.sql` | Dữ liệu demo（`E'…\n…'`, `setval` sau khi chèn id cố định） |
| `pom.xml` | MySQL → `postgresql`, thêm `spring-boot-starter-session-jdbc` |
| `application.properties` | `DB_URL / DB_USERNAME / DB_PASSWORD`, pool nhỏ, session JDBC, `server.port=${PORT:8080}`, forward headers |
| `Dockerfile`, `.dockerignore` | Build Maven → chạy bằng JRE 21, giờ Nhật, user không phải root |

| `vercel.json` | Region Tokyo（`hnd1`） |
| `README.md` | 使用技術・セキュリティ・デプロイ・起動方法（Supabase）を更新 |
| `.gitignore` | Thêm `.env` |

File MySQL cũ（`database/schema.sql`…）giữ lại để tham khảo.

**Đã kiểm tra（PostgreSQL 16 trong máy ảo của Claude）:** `01 → 02 → 03` chạy OK, chạy lại lần 2 OK; `jobtrack_app` đọc/ghi được, trigger `updated_at` chạy, `anon` bị chặn（permission denied）.
**Chưa kiểm tra:** build Java（không tải được thư viện Maven trong máy ảo）→ Khang Rebuild trên IntelliJ.

## Các bước

### A. Supabase（dev）
- [ ] supabase.com → đăng nhập bằng GitHub → **New project**: tên `jobtrack-dev`, region **Northeast Asia (Tokyo)**, Database password: bấm Generate → lưu vào nơi an toàn（**không dán vào chat / Git**）
- [ ] **SQL Editor** → chạy lần lượt `01_schema.sql` → `02_security.sql`（đổi `CHANGE_ME` thành mật khẩu mới cho `jobtrack_app`, không lưu file đã đổi）→ `03_seed.sql`
- [ ] **Connect** → **Session pooler** → lấy host / port / project-ref
- [ ] IntelliJ（Run Configuration + template JUnit）: thay `DB_PASSWORD` cũ bằng
      `DB_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require`
      `DB_USERNAME=jobtrack_app.<project-ref>`
      `DB_PASSWORD=<mật khẩu jobtrack_app>`
- [ ] Maven → **Reload project**（pom.xml thay đổi）→ Rebuild → test → chạy app → đăng nhập demo

### B. Supabase（prod）
- [ ] Tạo `jobtrack-prod` giống A（seed: tuỳ chọn, có thể chỉ tạo tài khoản mới）

### C. Vercel
- [ ] vercel.com → đăng nhập bằng GitHub → **Add New Project** → chọn repo `jobtrack` → Framework: **Container**（tự nhận Dockerfile）
- [ ] Environment Variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`（của prod）, `GROQ_API_KEY`, `PORT=8080`
- [ ] Settings → Functions → Region: **Tokyo (hnd1)**（gần Supabase Tokyo）
- [ ] Deploy → mở URL → đăng nhập → thử các màn hình + AI

### D. Hoàn thiện
- [ ] README（起動方法・使用技術・デモURL）, difficulties.md
- [ ] Commit + push

## Câu hỏi phỏng vấn nên chuẩn bị

| Câu hỏi | Ý chính |
|---|---|
| なぜ MySQL から PostgreSQL に？ | 無料で使えるクラウドDB（Supabase）に合わせた。JPA を使っていたので Java のコードはほぼ変更なし、SQL の違い（自動採番・更新日時）だけ対応 |
| サーバーレスで困ったことは？ | アクセスがないとアプリが止まり、メモリのセッションが消える → セッションを DB に保存（Spring Session JDBC） |
| セキュリティは？ | Supabase の REST API を RLS で閉じ、アプリ専用の最小権限ユーザーで接続。パスワード・APIキーは環境変数 |
