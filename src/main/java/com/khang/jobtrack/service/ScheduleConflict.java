package com.khang.jobtrack.service;

import com.khang.jobtrack.entity.Interview;
import com.khang.jobtrack.entity.PartTimeShift;

/**
 * シフトと面接の時間が重なっている組み合わせ。
 * record: フィールド・コンストラクタ・アクセサ（shift(), interview()）を自動生成するクラス（Java 16+）
 */
public record ScheduleConflict(PartTimeShift shift, Interview interview) {
}
