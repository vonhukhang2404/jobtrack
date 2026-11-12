package com.khang.jobtrack.ai;

/**
 * AI機能で発生したエラー（APIキー未設定、通信エラー、応答の形式不正など）。
 * メッセージはそのまま画面に表示するので、利用者向けの日本語で書く。
 */
public class AiException extends RuntimeException {
    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
