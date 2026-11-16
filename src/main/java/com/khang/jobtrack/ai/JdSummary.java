package com.khang.jobtrack.ai;

import java.util.List;

/**
 * AIが作った求人票の解説（表示するだけで保存はしない。AIの結果は ai_results にキャッシュされる）
 *
 * @param summary    どんな仕事か（2〜3文）
 * @param work       主な仕事内容
 * @param skills     必要なスキル・歓迎スキル
 * @param conditions 勤務地・給与・勤務時間などの条件
 * @param prepare    面接までに準備するとよいこと
 */
public record JdSummary(String summary, List<String> work, List<String> skills,
                        List<String> conditions, List<String> prepare) {
}
