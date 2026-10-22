package com.khang.jobtrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CompanyForm {

    @NotBlank(message = "企業名を入力してください")
    @Size(max = 100, message = "100文字以内で入力してください")
    private String name;

    @Size(max = 50, message = "50文字以内で入力してください")
    private String industry;

    @Size(max = 255, message = "255文字以内で入力してください")
    private String url;

    private String memo;   // TEXT → không cần giới hạn

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}