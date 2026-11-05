package com.khang.jobtrack.entity;

public enum InterviewResult {

    PENDING("結果待ち"),
    PASSED("通過"),
    FAILED("不通過");

    private final String label;

    InterviewResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
