package com.khang.jobtrack.entity;

public enum ApplicationStatus {

    NOT_APPLIED("未応募"),
    ENTRY("エントリー"),
    DOCUMENT("書類選考"),
    FIRST_INTERVIEW("一次面接"),
    SECOND_INTERVIEW("二次面接"),
    FINAL_INTERVIEW("最終面接"),
    OFFER("内定"),
    REJECTED("不合格"),
    DECLINED("辞退");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }
    
    public String getLabel() {
        return label;
    }
}