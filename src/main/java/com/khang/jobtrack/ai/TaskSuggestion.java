package com.khang.jobtrack.ai;

import java.time.LocalDate;

/** AIが求人票から見つけたタスク候補（まだ保存していない） */
public record TaskSuggestion(String title, LocalDate dueDate) {
}
