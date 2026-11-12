package com.khang.jobtrack.entity;

public enum AiResultType {
    JD_SUMMARY,  // 求人票の要約（やさしい日本語・ベトナム語）
    QUESTIONS,   // 想定質問
    AUTO_FILL,   // 求人票から締切を抽出してタスク化
    ES_REVIEW    // ESの添削（追加機能）
}
