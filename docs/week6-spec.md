# Tuần 6 – AI: giải thích 求人票 + câu hỏi phỏng vấn dự đoán（11/16〜11/22）

## Tính năng

| | Tính năng | URL | Đầu vào → AI → Kết quả |
|---|---|---|---|
| A | 📖 求人票の解説 | `/applications/{id}/ai-summary` | 求人票 + ngôn ngữ (やさしい日本語 / Tiếng Việt) → JSON `{summary, work[], skills[], conditions[], prepare[]}` → hiển thị theo thẻ |
| B | 💬 面接の想定質問 | `/applications/{id}/ai-questions` | 職種 + 求人票 + ghi chú các buổi 面接 trước → JSON `{questions:[{question, intent, hint}]}` → 5 câu hỏi |

| C | ✍ 回答の添削 | `POST /applications/{id}/ai-questions/review` | 質問 + 意図 + 自分の回答 → JSON `{goodPoints[], improvements[], revised}` |

Thêm: nút 「🔄 別の質問を作る」 (gửi các câu đã ra → AI tạo câu khác).

Nút: 応募一覧（🤖 締切 / 📖 解説 / 💬 質問）, 面接一覧（💬 質問）.

Khác tuần 5: kết quả **chỉ để đọc**, không lưu thành dữ liệu. Kết quả AI vẫn được cache trong `ai_results` (type `JD_SUMMARY` / `QUESTIONS`).

## Thiết kế

```
Controller ─→ JdSummaryService / InterviewQuestionService / AiTaskService
                   │ buildPrompt → parse（mỗi tính năng tự có）
                   ↓
              AiResultCacheService.getOrGenerate(application, type, prompt)   ← dùng chung
                   ↓ (chưa có trong ai_results)
              AiClient（GroqClient / GeminiClient）
```

| Quyết định | Lý do |
|---|---|
| Tách cache ra `AiResultCacheService` (refactor từ `AiTaskService`) | 3 tính năng dùng chung → không lặp code (DRY), test cache 1 chỗ |
| Cache key = prompt | Đổi ngôn ngữ hoặc thêm ghi chú 面接 → prompt khác → tự gọi AI lại |
| Kiểm tra quyền sở hữu trước khi đọc 面接 | `findByApplicationIdOrderByRoundAscIdAsc` không có userId → luôn gọi `applicationService.findOwned` trước |
| Không có 求人票 lẫn ghi chú 面接 → không gọi AI | Không có gì để AI dựa vào → dễ bịa |
| Câu hỏi bằng 日本語 lịch sự, hint bằng やさしい日本語 | Luyện đúng ngôn ngữ phỏng vấn, nhưng gợi ý dễ hiểu cho du học sinh |

## File（11/16 – Claude）

| File | Nội dung |
|---|---|
| `service/AiResultCacheService` | Cache dùng chung (tách từ `AiTaskService.findOrCallAi`) |
| `ai/SummaryLanguage`, `ai/JdSummary`, `service/JdSummaryService` | Tính năng A |
| `ai/InterviewQuestion`, `service/InterviewQuestionService` | Tính năng B (+ 別の質問: `excluded`, `cleanExcluded`) |
| `ai/AnswerReview`, `service/AnswerReviewService`, `templates/ai/review.html` | Tính năng C（type `ES_REVIEW`） |
| `controller/AiSummaryController`, `controller/AiQuestionController` | Màn hình |
| `templates/ai/summary.html`, `templates/ai/questions.html` | |
| `repository/InterviewRepository` | Thêm `findByApplicationIdOrderByRoundAscIdAsc` |
| `applications/list.html`, `interviews/list.html` | Nút 📖 / 💬 |
| Test | `AiResultCacheServiceTest` (2), `JdSummaryServiceTest` (8), `InterviewQuestionServiceTest` (9), `AnswerReviewServiceTest` (5), sửa `AiTaskServiceTest` (10) → tổng 47 |

## Checklist

- [x] Rebuild + toàn bộ test xanh（40/40, 11/16）
- [x] Chạy thử A: やさしい日本語 và Tiếng Việt（ウメダ銀行, 11/16）
- [x] Chạy thử B: 応募 có ghi chú 面接（サクラシステム, 11/16）→ cải thiện prompt「新卒」
- [x] Bấm lần 2 → nhanh (cache 29ms)
- [x] `/applications/9999/ai-summary`, `/applications/9999/ai-questions` → 404
- [x] README, difficulties.md（mục 40–45）
- [x] Chạy lại B sau khi sửa prompt「新卒」→ không còn câu hỏi về 実務（câu hỏi về 授業・個人開発）
- [x] 別の質問を作る → 5 câu khác hoàn toàn
- [x] 回答の添削 → phát hiện AI bịa thành quả → sửa prompt → chạy lại OK（difficulties #44）
- [x] Toàn bộ test 47/47
- [x] Commit + push（`6d1dc27`）

## Thêm: 📥 資料の取り込み（11/16）

```
/imports → file .docx / .txt hoặc dán văn bản → AI tách theo công ty（totalCount để phát hiện bỏ sót）
→ xác nhận（tick・sửa, nhãn 登録済み, link 🔍 tìm kiếm）→ @Transactional lưu: 企業 + 応募（未応募, 求人票）+ タスク説明会
```

| File | Nội dung |
|---|---|
| `importer/DocxTextExtractor`, `DocumentReadException` | Đọc .docx bằng JDK, chống XXE / ZIP bomb |
| `service/CompanyImportService` | prompt, parse（totalCount, N1→日本語, 職種「ほか」）, `toFormItems`, `save`（@Transactional） |
| `dto/CompanyImportForm`, `controller/ImportController`, `templates/imports/index.html` | Màn hình |
| `CompanyRepository.findByUserIdAndName`, `GroqClient`（max_completion_tokens）, navbar, `application.properties`（upload 5MB） | |
| Test | `DocxTextExtractorTest`（4）, `CompanyImportServiceTest`（11）→ tổng 62 |

- [x] Tài liệu thầy gửi（9 công ty）→ đủ 9, ngày đúng（năm 2027 cho tháng 1）
- [x] Lưu 2 công ty → 企業 2, 応募 2, タスク 2
- [x] 💬 質問 cho KDDIアイレット（クラウドインフラ）→ câu hỏi về cloud / server
- [x] Rebuild + test 62/62（sau khi sửa N1）
- [x] Khang tải file .docx → 9 công ty → lưu tất cả → phát hiện 応募 trùng（difficulties #50）→ sửa
- [x] Rebuild + test 63/63（sau khi sửa trùng）
- [x] Import lại DMM → 「応募 0 件（既存 1 件）」, mặc định không tick ✅ / AI đổi cách viết 職種 → dùng 職種 của 応募 có sẵn
- [x] Rebuild + test 64/64 → import lại DMM: 職種 mặc định = 応募 có sẵn, hiện 「既存の応募」 ✅
- [x] Xoá 2 応募 trùng（DMM.com, KDDIアイレット）
- [ ] Commit + push

## Câu hỏi phỏng vấn nên chuẩn bị

| Câu hỏi | Ý chính |
|---|---|
| 留学生向けの工夫は？ | 求人票をやさしい日本語・ベトナム語で解説。想定質問は面接と同じ丁寧な日本語、ヒントはやさしい日本語 |
| 3つのAI機能で共通化したところは？ | `AiClient`（提供元の切り替え）と `AiResultCacheService`（キャッシュ）。機能ごとに違うのはプロンプトと回答のチェックだけ |
| 想定質問の精度を上げる工夫は？ | 前の面接メモを入れて「深掘り」と「まだ聞かれていないテーマ」をバランスよく作らせる。応募者は新卒と伝える |
| AIの添削で気をつけたことは？ | 本人の回答にない事実を足させない。足りない所は【 】で本人が書く。実際に動かして見つけた問題 |
