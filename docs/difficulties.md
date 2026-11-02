# JobTrack – Nhật ký khó khăn（苦労した点）

Ghi lại các vấn đề gặp phải trong quá trình làm, nguyên nhân và cách giải quyết.
Dùng để ôn lại và trả lời câu hỏi phỏng vấn「開発で苦労した点は？」.

---

## Tổng kết（3 tuần đầu: 2026-10-12 〜 11-01）

### Phân loại theo chủ đề

| Chủ đề | Mục | Điểm chính |
|---|---|---|
| Môi trường / DB | 1, 2, 3, 6 | Bảng mã `utf8mb4` trên Windows, script chạy lại được (冪等性), dọn user MySQL, cấu hình SQL dialect của IDE |
| Quản lý bí mật / Git | 4, 5, 21, 22 | Không commit mật khẩu thật, dùng biến môi trường; đổi email commit sang noreply trước khi public; ký tự xuống dòng (CRLF/LF) |
| Spring cơ bản | 7, 8, 11, 25 | Thiếu annotation thì Spring bỏ qua class; đọc kỹ package khi import; đường dẫn class + method được nối lại |
| Spring Security | 9 | Trang login riêng, logout bằng POST (CSRF) |
| Validation / dữ liệu nhập | 10, 12, 14 | Giới hạn theo cột DB và BCrypt (72 byte); chuỗi rỗng → `NULL`; kiểm tra trùng trừ chính nó (`IdNot`) |
| JPA / SQL | 13, 17, 23, 24, 27 | Sắp xếp ổn định bằng `id`; `@EntityGraph` chống N+1; `@JoinColumn` cho quan hệ; tên method = đường đi qua field; `@Query` + `GROUP BY` |
| Controller / màn hình | 15, 16, 25 | Chiều copy `entity ⇄ form`, PRG (`redirect:`), thêm lại dữ liệu Model khi hiện lại form |
| **Bảo mật** | **18**, 19 | **IDOR** qua URL và qua form, kiểm tra quyền ở server; không lộ stack trace |
| Java cơ bản | 26 | `int` không tự đổi thành `Long` (`0` vs `0L`) |
| Test | 28, 29 | Kiểm tra điều kiện test có đúng không; unit test bằng Mock, `contextLoads` cần DB |
| **Định vị sản phẩm** | **20** | Giải thích khác biệt với Google Calendar / ChatGPT; tài liệu phải khớp với thực tế |

### Bài học chung

| # | Bài học |
|---|---|
| 1 | **"Chạy được" chưa phải là "đúng"** (mục 8, 12, 13): phải mở DB, đọc log SQL để kiểm tra dữ liệu thật |
| 2 | **Không tin dữ liệu từ client** (mục 18): mọi ID từ URL / form / request đều phải kiểm tra chủ sở hữu ở server |
| 3 | **Ràng buộc ở DB phải lặp lại ở tầng ứng dụng** (mục 10, 12): `VARCHAR(100)` ↔ `@Size(max = 100)` để người dùng nhận thông báo dễ hiểu thay vì lỗi 500 |
| 4 | **Copy code cũ phải liệt kê chỗ khác biệt trước** (mục 11, 15): tên biến, URL, chiều gán dữ liệu |
| 5 | **Thử tấn công chính ứng dụng của mình** (mục 18): sửa URL, sửa HTML bằng F12 |

### 5 câu trả lời phỏng vấn nên học thuộc

| Chủ đề | Câu trả lời |
|---|---|
| Bảo mật (mục 18) | 「他のユーザーのデータにアクセスできないよう、全ての検索条件にログインユーザーのIDを含めました。URLのIDだけでなく、フォームから送られる企業IDもサーバー側で所有者を確認し、開発者ツールでHTMLを書き換えて攻撃できないことも確認しました。」 |
| パフォーマンス (mục 17) | 「一覧画面でN+1問題が起きないよう、@EntityGraphで関連データを一度のSQLで取得し、実際のSQLもログで確認しました。」 |
| データ品質 (mục 12) | 「未入力の項目が空文字で保存されていたため、前後の空白を除去し空文字をNULLに変換する処理を追加しました。」 |
| 入力チェック (mục 10) | 「入力チェックでは、DBの列の長さやBCryptの制限（72バイト）に合わせて文字数を制限しました。」 |
| 環境構築 (mục 1, 2) | 「Windows環境で日本語データの文字化けが発生したため、接続の文字コードをutf8mb4に統一しました。また、seedスクリプトは何度実行しても同じ結果になるよう改善しました。」 |

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

## Tuần 2

### 11. Import nhầm `Model` và URL bị lặp `/companies/companies`

- **Ngày:** 2026-10-19
- **Hiện tượng:** Controller báo lỗi khi gọi `model.addAttribute`; sau khi sửa thì mở `/companies` bị 404.
- **Nguyên nhân:** (1) IntelliJ tự import `ch.qos.logback.core.model.Model` thay vì `org.springframework.ui.Model`. (2) Class có `@RequestMapping("/companies")` mà method lại ghi `@GetMapping("/companies")` → URL thực tế là `/companies/companies`.
- **Cách giải quyết:** Đổi import; method chỉ ghi `@GetMapping` (không có đường dẫn).
- **Bài học:** Khi IntelliJ gợi ý import, phải đọc **tên package**. Đường dẫn ở class và ở method được **nối lại** với nhau.

### 12. Ô để trống được lưu thành chuỗi rỗng, dấu cách lọt qua kiểm tra trùng

- **Ngày:** 2026-10-22
- **Hiện tượng:** Để trống 業界/URL thì DB lưu `''` chứ không phải `NULL`. Nhập 「テスト株式会社␣」 (có dấu cách) thì không bị báo trùng.
- **Nguyên nhân:** Form HTML luôn gửi mọi ô, ô trống được gửi thành `""`; Spring gán nguyên giá trị đó, không cắt khoảng trắng.
- **Cách giải quyết:** `@InitBinder` + `StringTrimmerEditor(true)` trong Controller: cắt khoảng trắng, chuỗi rỗng → `null`.
- **Bài học:** Lỗi "chạy được nhưng dữ liệu sai". Phải **mở DB kiểm tra** chứ không chỉ nhìn màn hình.
- **Câu trả lời phỏng vấn:**
  「未入力の項目が空文字で保存されていたため、前後の空白を除去し空文字をNULLに変換する処理を追加しました。」

### 13. Thứ tự danh sách thay đổi mỗi lần tải lại

- **Ngày:** 2026-10-22
- **Hiện tượng:** 5 công ty mẫu hiển thị theo thứ tự khác nhau giữa các lần.
- **Nguyên nhân:** `seed.sql` INSERT trong cùng 1 giây → `created_at` bằng nhau → MySQL không đảm bảo thứ tự.
- **Cách giải quyết:** Thêm điều kiện sắp xếp phụ: `OrderByCreatedAtDescIdDesc`.
- **Bài học:** Khi sắp xếp, luôn thêm một cột duy nhất (id) ở cuối để kết quả ổn định (quan trọng khi phân trang).

### 14. Sửa công ty bị báo trùng tên với chính nó

- **Ngày:** 2026-10-22
- **Hiện tượng:** Giữ nguyên tên, chỉ sửa メモ → 「この企業は既に登録されています」.
- **Nguyên nhân:** `existsByUserIdAndName` tìm thấy chính bản ghi đang sửa.
- **Cách giải quyết:** Thêm `existsByUserIdAndNameAndIdNot` (`id <> ?`) dùng riêng cho chức năng sửa.

### 15. Copy dữ liệu ngược chiều, quên `redirect:`

- **Ngày:** 2026-10-22
- **Hiện tượng:** (1) Form sửa hiện trống. (2) Sau khi lưu bị lỗi 500 "template might not exist".
- **Nguyên nhân:** (1) Viết `company.setName(form.getName())` thay vì `form.setName(company.getName())`. (2) `return "/companies"` → Spring hiểu là **tên template**, không phải URL.
- **Cách giải quyết:** Đảo chiều copy; POST thành công thì `return "redirect:/companies"` (PRG pattern).
- **Bài học:** `A.setX(B.getX())` = lấy từ B ghi vào A. Nếu nằm trong `@Transactional`, copy ngược sẽ **xoá trắng dữ liệu DB** (dirty checking).

### 16. Hiện lại form khi có lỗi thì mất dữ liệu trong Model

- **Ngày:** 2026-10-22, 2026-10-24
- **Hiện tượng:** Form sửa khi báo lỗi lại biến thành form thêm mới; dropdown 企業/ステータス bị trống.
- **Nguyên nhân:** `return "xxx/form"` ở nhánh lỗi tạo request mới, Model không còn `companyId` / `companies` / `statuses` từ lần GET trước.
- **Cách giải quyết:** Thêm lại vào Model ở **mọi chỗ** `return` về form; gom vào method `addFormOptions()` (DRY).

### 17. Lazy loading và vấn đề N+1

- **Ngày:** 2026-10-23
- **Hiện tượng:** Danh sách 応募 cần hiện tên công ty (`a.company.name`) trong khi `company` là `LAZY` và `open-in-view=false`.
- **Nguyên nhân:** Không tải trước thì sẽ bị `LazyInitializationException`, hoặc (nếu bật open-in-view) mỗi dòng phát sinh thêm 1 câu SQL (N+1).
- **Cách giải quyết:** `@EntityGraph(attributePaths = "company")` → chỉ 1 câu SQL có JOIN. Đã xác nhận bằng log SQL (`show-sql=true`).
- **Câu trả lời phỏng vấn:**
  「一覧画面でN+1問題が起きないよう、@EntityGraphで関連する企業を一度のSQLで取得しました。実際に発行されるSQLもログで確認しています。」

### 18. IDOR: truy cập / gắn dữ liệu vào tài khoản người khác

- **Ngày:** 2026-10-22, 2026-10-24
- **Hiện tượng (nếu không chặn):** (S1) Đổi số trên URL `/applications/1/edit` → xem/sửa hồ sơ người khác. (S2) Dùng F12 sửa `<option value>` của dropdown 企業 → hồ sơ bị gắn vào công ty người khác.
- **Nguyên nhân:** Server tin vào ID do client gửi lên (URL, form, hoặc request gửi thẳng bằng curl).
- **Cách giải quyết:** Mọi truy vấn đều lọc theo user đăng nhập: `findByIdAndUserId`, `findByIdAndCompanyUserId`; `companyId` trong form cũng phải qua `companyService.findOwned()`. Không thuộc user → 404.
- **Bài học:** Broken Access Control là #1 OWASP Top 10. Kiểm tra quyền phải làm ở **server**, giao diện không phải là bảo mật.
- **Câu trả lời phỏng vấn:**
  「他のユーザーのデータにアクセスできないよう、全ての検索条件にログインユーザーのIDを含めました。URLのIDだけでなく、フォームから送られる企業IDもサーバー側で所有者を確認しています。開発者ツールでHTMLを書き換えて実際に攻撃できないことも確認しました。」

### 19. Trang lỗi hiển thị stack trace

- **Ngày:** 2026-10-22, 2026-10-26
- **Hiện tượng:** Lỗi 404 hiện Whitelabel Error Page kèm tên package, class, số dòng.
- **Nguyên nhân:** Chưa có trang lỗi riêng; DevTools bật hiển thị chi tiết lỗi.
- **Cách giải quyết:** Tạo `templates/error/404.html` và `error/error.html` (Spring Boot tự dùng, không cần code Java).
- **Bài học:** Không để lộ thông tin nội bộ cho người dùng.

---

## Tuần 3

### 20. Thầy hỏi: "Khác gì Google Calendar / ChatGPT?" và 企画書 không khớp thực tế

- **Ngày:** 2026-10-26
- **Hiện tượng:** Khi thầy kiểm tra dự án, không giải thích được rõ JobTrack khác gì Google Calendar, ChatGPT. Thầy hỏi 企画書 có viết bằng AI không. Một số dòng trong 企画書 không đúng thực tế (ghi MySQL dùng Docker, ghi đã chặn đổi trạng thái không hợp lệ).
- **Nguyên nhân:** Chưa phân tích công cụ có sẵn; định vị AI là "chatbot" nên trùng với ChatGPT; 企画書 viết theo kế hoạch, không cập nhật theo thực tế.
- **Cách giải quyết:** Định vị lại: Calendar quản lý "thời gian", JobTrack quản lý "tiến độ xin việc"; AI không trò chuyện mà xử lý dữ liệu đã lưu và trả kết quả thành dữ liệu (trích hạn nộp → tạo task); kết nối Calendar thay vì cạnh tranh. Sửa 企画書 v2 cho khớp thực tế, ghi rõ có dùng AI để chỉnh văn bản.
- **Bài học:** Câu 「既存サービスとの違いは？」 chắc chắn sẽ bị hỏi. Tài liệu phải viết ở mức mình **giải thích được bằng lời**.
- **Câu trả lời phỏng vấn:**
  「Googleカレンダーは“時間”を管理しますが、JobTrackは企業ごとの選考状況や締切タスクなど“就活の進み具合”を管理します。予定はカレンダーに出力して連携します。」

### 21. Email cá nhân nằm trong lịch sử commit

- **Ngày:** 2026-10-26
- **Hiện tượng:** Trước khi public lên GitHub, 12 commit đều ghi email Gmail thật.
- **Cách giải quyết:** Bật 「Keep my email addresses private」 trên GitHub, `git config user.email` sang email noreply, sửa lại tác giả toàn bộ commit bằng `git filter-branch --env-filter`, xoá bản sao lưu `refs/original`. Kiểm tra lại toàn bộ lịch sử không có mật khẩu trước khi push.
- **Bài học:** Viết lại lịch sử chỉ an toàn **trước** khi push. Public repo thì kiểm tra cả **lịch sử**, không chỉ file hiện tại.

### 22. File không sửa gì nhưng Git báo thay đổi toàn bộ

- **Ngày:** 2026-10-27
- **Hiện tượng:** `Company.java`, `User.java` hiện "sửa 90 dòng" dù nội dung code giống hệt.
- **Nguyên nhân:** Ký tự xuống dòng bị đổi (Windows `CRLF` ⇄ `LF`).
- **Cách giải quyết:** Rollback 2 file, không đưa vào commit.
- **Bài học:** Trước khi commit phải xem **danh sách file và diff**, không tick tất cả.

### 23. Dùng sai annotation trong Entity

- **Ngày:** 2026-10-27
- **Hiện tượng:** `Task.java` dùng `@Column` cho quan hệ `@ManyToOne`, và `@NotNull` thay cho `@Column(nullable = false)`.
- **Nguyên nhân:** Nhầm giữa annotation của JPA (mô tả DB) và Bean Validation (kiểm tra dữ liệu nhập).
- **Cách giải quyết:** Quan hệ dùng `@JoinColumn(name = "application_id", nullable = false)`; NOT NULL ở DB dùng `@Column(nullable = false)`; `@NotNull` để ở Form.
- **Bài học:** **Entity mô tả DB, Form kiểm tra người dùng nhập.** `@NotNull` trên kiểu `boolean` là vô nghĩa (kiểu nguyên thuỷ không bao giờ `null`).

### 24. Tên method Repository sai đường đi

- **Ngày:** 2026-10-27, 10-29
- **Hiện tượng:** `findByUserId` cho Task, `countByUserId` cho Application → app không khởi động (`No property 'user' found`). Lỗi chính tả `CreateAt`. Quên `extends JpaRepository`.
- **Nguyên nhân:** Task / Application không có field `user`; phải đi qua các quan hệ.
- **Cách giải quyết:** Tên method = **đường đi qua các field**: Company → `UserId`, Application → `CompanyUserId`, Task → `ApplicationCompanyUserId`.
- **Bài học:** Tên method dựa vào **tên field trong Entity**, không phải tên cột DB hay URL. Spring kiểm tra lúc khởi động nên restart là biết ngay.

### 25. Copy Controller nhưng sót tên Model và annotation

- **Ngày:** 2026-10-27
- **Hiện tượng:** (1) Bấm 登録 → **405** (thiếu `@PostMapping` ở `create`). (2) `/tasks` lỗi 500 (thiếu `today` trong Model). (3) Form lỗi 500 (`addFormOptions` đưa `tasks` thay vì `applications`).
- **Nguyên nhân:** Copy từ `ApplicationController` mà không đối chiếu với spec của HTML.
- **Cách giải quyết:** Đối chiếu **tên trong Model** với `${...}` trong HTML.
- **Bài học:** Field được inject mà **không dùng ở đâu** (IntelliJ tô xám) là dấu hiệu còn thiếu một bước.

### 26. `0` và `0L`

- **Ngày:** 2026-10-29
- **Hiện tượng:** `Map<ApplicationStatus, Long>.put(status, 0)` báo lỗi compile.
- **Nguyên nhân:** `0` là `int`, chỉ tự đóng gói thành `Integer`, không thành `Long`.
- **Cách giải quyết:** `0L`.

### 27. Đếm theo trạng thái: tên method không diễn tả được `GROUP BY`

- **Ngày:** 2026-10-29
- **Nội dung:** Dashboard cần số hồ sơ theo từng trạng thái.
- **Cách giải quyết:** Viết JPQL bằng `@Query` (`SELECT a.status AS status, COUNT(a) AS count ... GROUP BY a.status`), nhận kết quả bằng interface projection `StatusCount`. Trạng thái không có trong DB thì bù 0 bằng `EnumMap` (giữ đúng thứ tự enum).
- **Câu trả lời phỏng vấn:**
  「単純な検索はメソッド名から自動生成し、集計（GROUP BY）は@QueryでJPQLを書きました。DBに存在しないステータスも0件で表示するため、EnumMapで全ステータスを初期化しています。」

### 28. Test bảo mật bị sai điều kiện

- **Ngày:** 2026-10-29
- **Hiện tượng:** Test S1 ra 404, nhưng URL là `localhost:8080/localhost:8080/tasks/1/edit`. Một lần khác thì đang đăng nhập bằng **demo** (chủ của dữ liệu) nên mở được.
- **Nguyên nhân:** 404 do **URL không tồn tại**, không phải do kiểm tra quyền.
- **Bài học:** Kết quả đúng chưa chắc test đúng. Phải kiểm tra **điều kiện test** (tài khoản, URL) trước khi kết luận.

### 29. Unit test với JUnit + Mockito

- **Ngày:** 2026-10-29
- **Nội dung:** Viết 4 unit test cho `TaskService`, `DashboardService` bằng Mock (không cần DB). Test mặc định `contextLoads` cần MySQL nên lỗi khi chạy toàn bộ.
- **Cách giải quyết:** Thêm `DB_PASSWORD` vào template JUnit của IntelliJ (không lưu vào Git) → 5 test xanh. Thử "phá code" (xoá dòng kiểm tra quyền) để thấy test chuyển đỏ.
- **Câu trả lời phỏng vấn:**
  「JUnitとMockitoでServiceの単体テストを書きました。他人の応募IDではタスクが保存されないことをテストで確認しています。」

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
