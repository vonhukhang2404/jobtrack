package com.khang.jobtrack.ai;

/** 求人票を解説する言語 */
public enum SummaryLanguage {
    EASY_JA("やさしい日本語",
            "やさしい日本語（短い文で書き、むずかしい言葉や専門用語には、かんたんな説明を（）でつける）"),
    VI("Tiếng Việt",
            "ベトナム語（専門用語や日本の就職活動の用語は、日本語を（）で書き添える）");

    private final String label;
    private final String instruction; // プロンプトに入れる指示

    SummaryLanguage(String label, String instruction) {
        this.label = label;
        this.instruction = instruction;
    }

    public String getLabel() {
        return label;
    }

    public String getInstruction() {
        return instruction;
    }
}
