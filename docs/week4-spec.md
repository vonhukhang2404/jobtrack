# Tuần 4 – Spec phần Java（11/2〜11/8）

HTML đã làm xong. Viết Java **khớp đúng tên** trong bảng "Model" của từng phần.
Thứ tự mỗi phần: Enum → Entity → Repository → Form → Service → Controller.

| # | Chức năng | HTML | Trạng thái |
|---|---|---|---|
| 1 | 面接記録 | `interviews/list.html`, `interviews/form.html` | ⬜ |
| 2 | アルバイトのシフト | `shifts/list.html`, `shifts/form.html` | ⬜ |
| 3 | Cảnh báo trùng giờ / 28h | trong `shifts/list.html`, `index.html` | ⬜ |
| 4 | Xuất `.ics` | nút ở `interviews/list.html`, `tasks/list.html` | ⬜ |
| 5 | Dashboard: 面接 sắp tới | `index.html` | ⬜ |

> Các phần ③④⑤ trong HTML có kiểm tra `!= null`, nên **trang không lỗi** khi Java chưa viết, chỉ là chưa hiện.

### Đã viết sẵn（11/2）

| Phần | File | Còn lại cho bạn |
|---|---|---|
| 1. 面接記録 | `InterviewFormat`, `InterviewResult`, `Interview`, `InterviewRepository`, `InterviewForm` | **`InterviewService`, `InterviewController`** |
| 2. シフト | `ShiftForm`, SQL（`schema.sql`, `migrations/001_create_part_time_shifts.sql`, `seed.sql`） | Chạy SQL → **`PartTimeShift`（Entity）**, Repository, Service, Controller |
| 3〜5 | | Toàn bộ |

**Thứ tự chạy SQL（chỉ 1 lần）:**
1. `mysql -u root -p jobtrack < database/migrations/001_create_part_time_shifts.sql`
2. `mysql -u root -p --default-character-set=utf8mb4 jobtrack < database/seed.sql`（⚠ đặt lại toàn bộ dữ liệu, tài khoản khang bị xoá → đăng ký lại）

---

### Đã viết thêm（11/5 – Claude）
| File | Nội dung |
|---|---|
| `service/InterviewService`, `controller/InterviewController` | CRUD 面接 + check trùng 回 |
| `entity/PartTimeShift`, `repository/PartTimeShiftRepository` | ca làm thêm |
| `service/ScheduleConflict`, `service/WeeklyHours` | record kết quả |
| `service/ShiftService` | CRUD + **3 method TODO cho Khang** |
| `controller/ShiftController` | CRUD + check 終了 > 開始 |
| `controller/CalendarController` | `GET /calendar.ics` |
| `controller/HomeController` | thêm `upcomingInterviews`, `conflictCount` |
| `test/.../ShiftServiceTest` | 8 test cho 3 method TODO |

### Khang tự làm
- [ ] `ShiftService.overlaps`
- [ ] `ShiftService.findConflicts(List, List)`
- [ ] `ShiftService.calcWeeklyHours(List)`
- [ ] `ShiftServiceTest` xanh hết

## 1. 面接記録（bảng `interviews` đã có）

### Enum（package `entity`）

| Enum | Giá trị（label） |
|---|---|
| `InterviewFormat` | `ONLINE("オンライン")`, `OFFLINE("対面")` |
| `InterviewResult` | `PENDING("結果待ち")`, `PASSED("通過")`, `FAILED("不通過")` |

### Entity `Interview`

| Field | Kiểu | Cột / ghi chú |
|---|---|---|
| `application` | `Application` | `application_id`, `@ManyToOne(LAZY)` + `@JoinColumn` |
| `round` | `int` | `round` |
| `scheduledAt` | `LocalDateTime` | `scheduled_at` |
| `format` | `InterviewFormat` | `@Enumerated(STRING)`, mặc định `ONLINE` |
| `result` | `InterviewResult` | `@Enumerated(STRING)`, mặc định `PENDING` |
| `notes` | `String` | `TEXT` |
| `id`, `createdAt`, `updatedAt` | | như entity khác |

### Repository `InterviewRepository`

| Method | |
|---|---|
| Danh sách của user | `OrderByScheduledAtDescIdDesc`, `@EntityGraph({"application", "application.company"})` |
| 1 phỏng vấn của user | chống IDOR |
| `existsByApplicationIdAndRound` | kiểm tra trùng khi thêm |
| `existsByApplicationIdAndRoundAndIdNot` | kiểm tra trùng khi sửa |

### Form `InterviewForm`

| Field | Kiểu | Validation |
|---|---|---|
| `applicationId` | `Long` | `@NotNull`「応募を選択してください」 |
| `round` | `Integer` | `@NotNull`「何次面接かを入力してください」, `@Min(1)`「1以上で入力してください」 |
| `scheduledAt` | `LocalDateTime` | `@NotNull`「日時を入力してください」, `@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")` |
| `format` | `InterviewFormat` | `@NotNull`, mặc định `ONLINE` |
| `result` | `InterviewResult` | `@NotNull`, mặc định `PENDING` |
| `notes` | `String` | |

### Controller `/interviews`

| URL | Model |
|---|---|
| `GET /interviews` | `interviews`, `now`（`LocalDateTime.now()`） |
| `GET /new`, `POST`, `GET /{id}/edit`, `POST /{id}` | `interviewForm`, `applications`, `formats`（`InterviewFormat.values()`）, `results`, `interviewId`（khi sửa） |
| `POST /{id}/delete` | |

- Trùng vòng → `rejectValue("round", "duplicate", "この応募の面接は既に登録されています")`
- `applicationId` từ form phải qua `applicationService.findOwned`（S2）

---

## 2. アルバイトのシフト（bảng mới）

### 2-1. Bảng `part_time_shifts`（tự thiết kế, dưới đây là đề xuất）

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `user_id` | BIGINT | FK → `users`, NOT NULL, `ON DELETE CASCADE` |
| `workplace` | VARCHAR(100) | NOT NULL |
| `start_at` | DATETIME | NOT NULL |
| `end_at` | DATETIME | NOT NULL, **CHECK (`end_at` > `start_at`)** |
| `memo` | TEXT | NULL |
| `created_at`, `updated_at` | DATETIME | như các bảng khác |
| | | INDEX (`user_id`, `start_at`) |

- Thêm `CREATE TABLE` vào **`schema.sql`**（cho người cài mới）
- Tạo **`database/migrations/001_create_part_time_shifts.sql`** chỉ chứa `CREATE TABLE`, chạy trên DB **đang có dữ liệu**（**không** chạy lại `schema.sql` vì nó `DROP DATABASE`）
- Thêm vài ca mẫu vào `seed.sql`, trong đó **1 ca trùng giờ** với 二次面接 của サクラ（`2026-10-20 10:00`）để test cảnh báo

### 2-2. Entity `PartTimeShift`: `user`, `workplace`, `startAt`, `endAt`, `memo`

### 2-3. Form `ShiftForm`

| Field | Kiểu | Validation |
|---|---|---|
| `workplace` | `String` | `@NotBlank`, `@Size(max = 100)` |
| `startAt`, `endAt` | `LocalDateTime` | `@NotNull`, `@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")` |
| `memo` | `String` | |

- Trong Controller: `endAt` không sau `startAt` → `rejectValue("endAt", "order", "終了は開始より後にしてください")`

### 2-4. Controller `/shifts`

| URL | Model |
|---|---|
| `GET /shifts` | `shifts`（sắp `startAt` tăng dần）, `conflicts`, `conflictShiftIds`, `weeklyHours`（phần 3） |
| form | `shiftForm`, `shiftId`（khi sửa） |

---

## 3. Cảnh báo（**tự viết thuật toán**）

### 3-1. Trùng giờ ca × phỏng vấn

- Kiểu kết quả: `public record ScheduleConflict(PartTimeShift shift, Interview interview) {}`
- Model `conflicts` = `List<ScheduleConflict>`, `conflictShiftIds` = `Set<Long>`（id các ca bị trùng）
- Phỏng vấn chỉ có giờ bắt đầu → coi như kéo dài **1 tiếng**
- **Câu hỏi để tự nghĩ:** 2 khoảng `[A_start, A_end)` và `[B_start, B_end)` **giao nhau** khi nào? Viết điều kiện chỉ bằng **2 phép so sánh**.

### 3-2. Tổng giờ theo tuần（> 28 giờ là vượt）

- Kiểu kết quả: `public record WeeklyHours(LocalDate weekStart, double hours) { public boolean over() { return hours > 28; } }`
- Model `weeklyHours` = `List<WeeklyHours>`, sắp theo tuần
- Tuần bắt đầu **thứ Hai**: `date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))`
- Số giờ của 1 ca: `Duration.between(startAt, endAt).toMinutes() / 60.0`
- Gợi ý: gom bằng `Map<LocalDate, Double>`（hoặc `TreeMap` để tự sắp theo ngày）

> HTML gọi `c.shift()`, `w.weekStart()`, `w.hours()`, `w.over()` → đúng tên accessor của **record**.

---

## 4. Xuất `.ics`

- `GET /calendar.ics` → trả về **file**（không phải trang HTML）
- Nội dung: các **面接**（1 tiếng）và **タスク chưa xong**（sự kiện cả ngày）của user
- Header: `Content-Type: text/calendar; charset=UTF-8`, `Content-Disposition: attachment; filename="jobtrack.ics"`
- Định dạng tối thiểu:

```
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//JobTrack//JP
BEGIN:VEVENT
UID:interview-2@jobtrack
DTSTART:20261020T100000
DTEND:20261020T110000
SUMMARY:二次面接（サクラシステム株式会社）
END:VEVENT
BEGIN:VEVENT
UID:task-3@jobtrack
DTSTART;VALUE=DATE:20261019
SUMMARY:【締切】二次面接の逆質問を準備
END:VEVENT
END:VCALENDAR
```

- Gợi ý: method Controller trả về `ResponseEntity<String>`; mỗi dòng kết thúc bằng `\r\n`

---

## 5. Dashboard（thêm vào `HomeController`）

| Model | Nội dung |
|---|---|
| `upcomingInterviews` | `List<Interview>`: phỏng vấn từ bây giờ trở đi, sắp tăng dần |
| `conflictCount` | số phần tử của `conflicts`（dùng lại logic phần 3） |
