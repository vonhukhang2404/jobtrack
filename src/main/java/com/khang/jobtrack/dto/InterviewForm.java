package com.khang.jobtrack.dto;

import com.khang.jobtrack.entity.InterviewFormat;
import com.khang.jobtrack.entity.InterviewResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class InterviewForm {

    @NotNull(message = "応募を選択してください")
    private Long applicationId;

    @NotNull(message = "何次面接かを入力してください")
    @Min(value = 1, message = "1以上で入力してください")
    private Integer round;

    // <input type="datetime-local"> は「2026-11-05T14:00」の形式で送られる
    @NotNull(message = "日時を入力してください")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime scheduledAt;

    @NotNull(message = "形式を選択してください")
    private InterviewFormat format = InterviewFormat.ONLINE;

    @NotNull(message = "結果を選択してください")
    private InterviewResult result = InterviewResult.PENDING;

    private String notes;

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Integer getRound() {
        return round;
    }

    public void setRound(Integer round) {
        this.round = round;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public InterviewFormat getFormat() {
        return format;
    }

    public void setFormat(InterviewFormat format) {
        this.format = format;
    }

    public InterviewResult getResult() {
        return result;
    }

    public void setResult(InterviewResult result) {
        this.result = result;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
