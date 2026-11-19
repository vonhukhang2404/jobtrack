package com.khang.jobtrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CompanyForm {

    @NotBlank(message = "{valid.companyName.required}")
    @Size(max = 100, message = "{valid.max100}")
    private String name;

    @Size(max = 50, message = "{valid.max50}")
    private String industry;

    @Size(max = 255, message = "{valid.max255}")
    private String url;

    private String memo;

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