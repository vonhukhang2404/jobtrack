package com.khang.jobtrack.ai;

/**
 * 生成AIを呼び出すためのインターフェース。
 * 提供元（Groq / Gemini）は application.properties の ai.provider で切り替える。
 * AiTaskService はこのインターフェースだけを使うので、提供元を変えても影響しない。
 */
public interface AiClient {

    /**
     * @param prompt AIへの指示文
     * @return AIの回答（JSON形式を指定しているので JSON 文字列）
     */
    String generateJson(String prompt);
}
