package com.khang.jobtrack.ai;

/**
 * AIが作った想定質問
 *
 * @param question 質問（面接で使う丁寧な日本語）
 * @param intent   面接官がその質問で確認したいこと
 * @param hint     答えるときのポイント（やさしい日本語）
 */
public record InterviewQuestion(String question, String intent, String hint) {
}
