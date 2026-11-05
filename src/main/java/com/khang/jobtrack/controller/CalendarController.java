package com.khang.jobtrack.controller;

import com.khang.jobtrack.entity.Interview;
import com.khang.jobtrack.entity.Task;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.InterviewService;
import com.khang.jobtrack.service.ShiftService;
import com.khang.jobtrack.service.TaskService;
import com.khang.jobtrack.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 面接と未完了タスクを iCalendar（.ics, RFC 5545）形式で出力する。
 * Googleカレンダー等に「インポート」して使う（JobTrackはカレンダーと競合せず、連携する）。
 */
@Controller
public class CalendarController {

    private static final String CRLF = "\r\n"; // .ics の改行は CRLF と決まっている
    private static final DateTimeFormatter LOCAL_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String TZ = "Asia/Tokyo";

    private final UserService userService;
    private final InterviewService interviewService;
    private final TaskService taskService;

    public CalendarController(UserService userService, InterviewService interviewService, TaskService taskService) {
        this.userService = userService;
        this.interviewService = interviewService;
        this.taskService = taskService;
    }

    @GetMapping("/calendar.ics")
    public ResponseEntity<String> export(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByEmail(userDetails.getUsername());
        String stamp = LocalDateTime.now(ZoneOffset.UTC).format(LOCAL_DT) + "Z";

        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//JobTrack//JP");
        line(sb, "CALSCALE:GREGORIAN");

        // 面接：開始時刻 + 1時間の予定
        for (Interview i : interviewService.findAllByUser(user)) {
            LocalDateTime start = i.getScheduledAt();
            LocalDateTime end = start.plus(ShiftService.INTERVIEW_DURATION);
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:interview-" + i.getId() + "@jobtrack");
            line(sb, "DTSTAMP:" + stamp);
            line(sb, "DTSTART;TZID=" + TZ + ":" + start.format(LOCAL_DT));
            line(sb, "DTEND;TZID=" + TZ + ":" + end.format(LOCAL_DT));
            line(sb, "SUMMARY:" + escape("【面接" + i.getRound() + "次】"
                    + i.getApplication().getCompany().getName()));
            if (i.getNotes() != null) {
                line(sb, "DESCRIPTION:" + escape(i.getNotes()));
            }
            line(sb, "END:VEVENT");
        }

        // 未完了タスク：期限日の終日予定（DTEND は翌日。終日予定の決まり）
        for (Task t : taskService.findAllByUser(user)) {
            if (t.isDone() || t.getDueDate() == null) {
                continue;
            }
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:task-" + t.getId() + "@jobtrack");
            line(sb, "DTSTAMP:" + stamp);
            line(sb, "DTSTART;VALUE=DATE:" + t.getDueDate().format(DATE));
            line(sb, "DTEND;VALUE=DATE:" + t.getDueDate().plusDays(1).format(DATE));
            line(sb, "SUMMARY:" + escape("【期限】" + t.getTitle() + "（"
                    + t.getApplication().getCompany().getName() + "）"));
            line(sb, "END:VEVENT");
        }

        line(sb, "END:VCALENDAR");

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jobtrack.ics\"")
                .body(sb.toString());
    }

    private static void line(StringBuilder sb, String s) {
        sb.append(s).append(CRLF);
    }

    // テキスト中の \ ; , 改行 はエスケープが必要（RFC 5545 3.3.11）
    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }
}
