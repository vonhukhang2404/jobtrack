package com.khang.jobtrack.service;

import java.time.LocalDate;

/**
 * 1週間（月曜〜日曜）の勤務時間の合計。
 */
public record WeeklyHours(LocalDate weekStart, double hours) {

    /** 留学生の資格外活動の上限（週28時間） */
    public static final double LIMIT_HOURS = 28.0;

    public boolean over() {
        return hours > LIMIT_HOURS;
    }
}
