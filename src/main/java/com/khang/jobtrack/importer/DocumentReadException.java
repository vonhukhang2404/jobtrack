package com.khang.jobtrack.importer;

/** アップロードされたファイルを読めなかった（メッセージは画面に表示する） */
public class DocumentReadException extends RuntimeException {
    public DocumentReadException(String message) {
        super(message);
    }

    public DocumentReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
