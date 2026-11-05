package com.khang.jobtrack.entity;

public enum InterviewFormat {

    ONLINE("オンライン"),
    OFFLINE("対面");

    private final String label;

    InterviewFormat(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
