# Tuần 5 – AI（Groq）: 求人票 → trích deadline → tạo task（11/9〜11/15）

## Luồng

```
応募一覧「🤖 AI締切抽出」
  → GET  /applications/{id}/ai-tasks          xem 求人票 + nút「AIで締切を抽出する」
  → POST /applications/{id}/ai-tasks/extract  buildPrompt → (ai_results cache | AI) → parse → hiện đề xuất
  → người dùng sửa / bỏ tick
  → POST /applications/{id}/ai-tasks          lưu các dòng đã tick thành Task → /tasks
```

Nguyên tắc: **AI chỉ đề xuất, người dùng xác nhận rồi mới lưu.** Chỉ gửi 求人票, không gửi thông tin cá nhân.

## Chuẩn bị

| Việc | Ghi chú |
|---|---|
| Lấy API key ở console.groq.com → API Keys | Không commit, không dán vào chat |
| Biến môi trường `GROQ_API_KEY` | Windows + Run Configuration + template JUnit |
| (Tuỳ chọn) `GROQ_MODEL` | Mặc định `openai/gpt-oss-120b` (`llama-3.3-70b-versatile` đã bị Groq ngừng 2026/08/16) |
| (Tuỳ chọn) `AI_PROVIDER=gemini` + `GEMINI_API_KEY` | Đổi sang Gemini mà không sửa code |

## Đã viết sẵn（11/9 – Claude）

| File | Nội dung |
|---|---|
| `ai/AiClient` | Interface `generateJson(prompt)` – đổi nhà cung cấp AI chỉ bằng `ai.provider` |
| `ai/GroqClient` (mặc định) | Gọi Groq (OpenAI互換), key gửi bằng header `Authorization: Bearer`, timeout, đổi lỗi HTTP → `AiException` |
| `ai/GeminiClient` | Gọi Gemini API, dùng khi `ai.provider=gemini` |
| `ai/AiException`, `ai/TaskSuggestion` | Lỗi AI (hiện lên màn hình) / 1 đề xuất (title, dueDate) |
| `entity/AiResult`, `entity/AiResultType`, `repository/AiResultRepository` | Bảng `ai_results` (đã có từ tuần 1) |
| `dto/AiTaskForm` | Form xác nhận (danh sách: tick / title / dueDate) |
| `service/AiTaskService` | `suggest`, `saveSelected` + **3 method TODO cho Khang** |
| `controller/AiTaskController` | 3 URL ở trên, kiểm tra input dòng đã tick |
| `templates/ai/tasks.html` | Màn hình 求人票 → đề xuất → xác nhận |
| `applications/list.html`, `tasks/list.html` | Nút 🤖, thông báo "… 件登録しました" |
| `ApplicationRepository.findByIdAndCompanyUserId` | Thêm `@EntityGraph("company")` (màn hình AI hiện tên công ty) |
| `application.properties` | `ai.provider`, `groq.*`, `gemini.*` |
| `test/.../AiTaskServiceTest` | 11 test: `buildPrompt` / `parse` / cache |

## Logic chính（11/9 – Claude viết theo yêu cầu, Khang đọc hiểu để giải thích được）

- [x] `AiTaskService.buildPrompt` — thiết kế prompt
- [x] `AiTaskService.parse` — đọc JSON + kiểm tra (ngày sai, ngày quá khứ, title rỗng, tối đa 10)
- [x] `AiTaskService.findOrCallAi` — cache bằng `ai_results`
- [x] `AiTaskServiceTest`（11 test）

## Khang tự làm

- [ ] Đọc hiểu 3 method trên, tự giải thích lại được (xem câu hỏi phỏng vấn bên dưới)
- [ ] `AiTaskServiceTest` xanh hết
- [ ] Thử với 求人票 thật (dán vào 応募 → 編集)
- [ ] Test bảo mật: `/applications/{id người khác}/ai-tasks` → 404

## Câu hỏi phỏng vấn nên chuẩn bị

| Câu hỏi | Ý chính |
|---|---|
| AIを使うとき気をつけたことは？ | AIは間違える → 提案だけ、人が確認して保存。回答はJSON指定＋検証（日付・過去日・件数） |
| API料金・回数制限は？ | 同じ入力は ai_results に保存して再利用（キャッシュ） |
| セキュリティは？ | APIキーは環境変数、ヘッダーで送信。個人情報は送らない。他人の応募は404 |
