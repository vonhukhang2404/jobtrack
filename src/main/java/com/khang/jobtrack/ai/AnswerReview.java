package com.khang.jobtrack.ai;

import java.util.List;

/**
 * 面接の回答に対するAIの添削
 *
 * @param goodPoints   良い点
 * @param improvements 改善点
 * @param revised      修正例（本人の内容をもとに、面接で話せる丁寧な日本語に整えたもの）
 */
public record AnswerReview(List<String> goodPoints, List<String> improvements, String revised) {
}
