package com.khang.jobtrack.dto;

import com.khang.jobtrack.entity.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class ApplicationForm {
    @NotNull(message = "企業を選択してください")
    private Long companyId;

    @NotBlank(message = "職種を入力してください")
    @Size(max = 100, message = "100文字以内で入力してください")
    private String position;

    @NotNull(message = "ステータスを選択してください")
    private ApplicationStatus status = ApplicationStatus.NOT_APPLIED;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate appliedAt;

    private String jdText;

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public LocalDate getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDate appliedAt) {
        this.appliedAt = appliedAt;
    }

    public String getJdText() {
        return jdText;
    }

    public void setJdText(String jdText) {
        this.jdText = jdText;
    }
}
