# JobTrack – Nhật ký khó khăn（苦労した点）

Ghi lại các vấn đề gặp phải trong quá trình làm, nguyên nhân và cách giải quyết.
Dùng để ôn lại và trả lời câu hỏi phỏng vấn「開発で苦労した点は？」.

---

## Tuần 1

### 1. Lỗi tiếng Nhật khi nạp seed data (ERROR 1366)

- **Ngày:** 2026-10-12
- **Hiện tượng:** `mysql -u root -p jobtrack < seed.sql` → `ERROR 1366 (HY000): Incorrect string value: '\x86\xE3\x83...' for column 'name'`
- **Nguyên nhân:** Trên Windows, MySQL client mặc định gửi dữ liệu theo bảng mã của Windows (cp932), không phải UTF-8 → chữ Nhật trong file bị đọc sai.
- **Cách giải quyết:** Thêm `SET NAMES utf8mb4;` vào đầu `seed.sql` (hoặc chạy với `--default-character-set=utf8mb4`).
- **Bài học:** Luôn chỉ định bảng mã kết nối là `utf8mb4` khi làm việc với tiếng Nhật.
- **Câu trả lời phỏng vấn:**
  「Windows環境で日本語のテストデータを投入した際、文字コードの違いでエラーが発生しました。接続の文字コードをutf8mb4に指定して解決しました。」

### 2. Chạy lại seed bị lỗi trùng khóa (ERROR 1062)

- **Ngày:** 2026-10-12
- **Hiện tượng:** Chạy lại `seed.sql` → `ERROR 1062: Duplicate entry '1' for key 'users.PRIMARY'`
- **Nguyên nhân:** Lần chạy đầu, bảng `users` (chỉ có chữ ASCII) đã INSERT thành công, lỗi xảy ra ở bảng `companies` → dữ liệu bị lưu một nửa.
- **Cách giải quyết:** Thêm `TRUNCATE TABLE` cho tất cả bảng ở đầu `seed.sql` (tạm tắt `FOREIGN_KEY_CHECKS`) → chạy lại bao nhiêu lần cũng được.
- **Bài học:** Script dữ liệu nên viết sao cho chạy lại được nhiều lần (冪等性 – idempotent).
- **Câu trả lời phỏng vấn:**
  「途中で失敗しても再実行できるように、seedスクリプトの先頭で既存データを削除するよう改善しました。」

### 3. Dọn dẹp user MySQL cũ

- **Ngày:** 2026-10-12
- **Hiện tượng:** MySQL có nhiều user không rõ nguồn gốc (`capybara`, `SMCS`, `inventory_user`…), một số có host `%` (kết nối được từ mọi máy).
- **Cách giải quyết:** Dùng `SHOW GRANTS FOR` để xem user thuộc DB nào → `SHOW DATABASES` kiểm tra DB còn tồn tại không → `DROP USER` các user không còn dùng.
- **Bài học:** Tạo user riêng cho từng ứng dụng với quyền tối thiểu (最小権限の原則), xóa user khi bỏ dự án.

### 4. Mật khẩu suýt bị commit lên Git

- **Ngày:** 2026-10-12
- **Hiện tượng:** `create_app_user.sql` chứa mật khẩu thật ngay trước khi commit lần đầu.
- **Cách giải quyết:** Đổi lại thành `CHANGE_ME` trước khi commit. Spring Boot đọc mật khẩu từ biến môi trường `${DB_PASSWORD}` (đặt trong Run Configuration của IntelliJ).
- **Bài học:** Không bao giờ ghi mật khẩu thật vào file được quản lý bởi Git. Đã commit thì dù xóa sau vẫn còn trong lịch sử.

### 5. Biến môi trường có dấu nháy

- **Ngày:** 2026-10-12
- **Hiện tượng:** Nhập `DB_PASSWORD='xxx'` trong IntelliJ.
- **Nguyên nhân:** IntelliJ không tự bỏ dấu nháy → mật khẩu thành `'xxx'` (có cả dấu nháy).
- **Cách giải quyết:** Nhập không có dấu nháy: `DB_PASSWORD=xxx`.

### 6. File SQL bị IntelliJ format lại thành nhiều dòng

- **Ngày:** 2026-10-12
- **Hiện tượng:** `DROP DATABASE ...` bị tách thành `DROP` / `DATABASE ...`; xuất hiện 19 lỗi "Unable to resolve table" khi commit.
- **Nguyên nhân:** IntelliJ chưa biết SQL dialect (MySQL) và chưa kết nối DB.
- **Cách giải quyết:** Settings → SQL Dialects → Project SQL Dialect = MySQL; kết nối Data Source `jobtrack@localhost` trong bảng Database.
- **Bài học:** Cảnh báo của IDE không phải lúc nào cũng là lỗi thật — cần đọc nội dung để phân biệt.

### 7. Quên `@Configuration` → `@Bean` không chạy

- **Ngày:** 2026-10-15
- **Hiện tượng:** Viết `SecurityConfig` có `@Bean PasswordEncoder` nhưng thiếu `@Configuration` trên class.
- **Nguyên nhân:** Spring chỉ quét class có annotation (`@Configuration`, `@Service`, `@Controller`…). Không có nhãn → class bị bỏ qua, `@Bean` bên trong không được tạo.
- **Cách giải quyết:** Thêm `@Configuration`.
- **Bài học:** Annotation là "nhãn" để Spring nhận diện class — thiếu là Spring coi như không tồn tại.

### 8. `withUsername(user.getName())` thay vì email

- **Ngày:** 2026-10-16
- **Hiện tượng:** Đăng nhập vẫn chạy, nhưng danh tính người đăng nhập là tên ("Demo User") chứ không phải email.
- **Nguyên nhân:** Spring chỉ so mật khẩu khi đăng nhập; chuỗi trong `withUsername` là định danh dùng về sau (`authentication.getName()`).
- **Cách giải quyết:** Đổi thành `user.getEmail()` (email là UNIQUE, dùng được với `findByEmail`).
- **Bài học:** Lỗi "chạy được nhưng sai" là loại khó phát hiện nhất — cần hiểu dữ liệu sẽ được dùng ở đâu sau này.

### 9. Trang login riêng không hiện / `/logout` báo 404

- **Ngày:** 2026-10-16
- **Hiện tượng:** Đã có `login.html` nhưng vẫn hiện trang login mặc định; sau khi cấu hình thì mở `/logout` bằng URL bị 404.
- **Nguyên nhân:** Chưa có `securityFilterChain` với `loginPage("/login")`. Khi dùng trang login riêng, Spring không tạo trang xác nhận logout; logout phải gửi bằng **POST** (chống CSRF).
- **Cách giải quyết:** Thêm `securityFilterChain` (`loginPage`, `usernameParameter("email")`, `permitAll`); thêm form `th:action="@{/logout}" method="post"` vào trang chủ.
- **Câu trả lời phỏng vấn:**
  「ログアウトはCSRF対策のためPOSTで送信する必要があり、Thymeleafのth:actionで自動的にCSRFトークンを付与しています。」

### 10. Ô mật khẩu hiện 2 thông báo lỗi cùng lúc

- **Ngày:** 2026-10-16
- **Hiện tượng:** Để trống mật khẩu → hiện cả 「パスワードを入力してください」 và 「8文字以上…」.
- **Nguyên nhân:** Chuỗi rỗng vi phạm cả `@NotBlank` và `@Size(min = 8)`.
- **Cách giải quyết:** Bỏ `@NotBlank`, chỉ giữ `@Size(min = 8, max = 72)`. `max = 72` vì BCrypt chỉ xử lý tối đa 72 byte (quá dài sẽ lỗi 500).
- **Câu trả lời phỏng vấn:**
  「入力チェックでは、DBの列の長さやBCryptの制限（72バイト）に合わせて文字数を制限しました。」

---

<!-- Thêm mục mới theo mẫu:
### N. Tiêu đề
- **Ngày:**
- **Hiện tượng:**
- **Nguyên nhân:**
- **Cách giải quyết:**
- **Bài học:**
- **Câu trả lời phỏng vấn:**
-->
