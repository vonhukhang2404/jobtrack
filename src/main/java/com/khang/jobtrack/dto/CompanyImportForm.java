package com.khang.jobtrack.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 取り込みの確認画面のフォーム：企業ごとに「登録する／しない」と内容を修正できる */
public class CompanyImportForm {

    private List<Item> items = new ArrayList<>();

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static class Item {
        private boolean selected = true;
        private String name;          // 企業名
        private String industry;      // 業界
        private String position;      // 応募の職種
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate eventDate;  // 説明会の日付（タスクの締切）。null ならタスクを作らない
        private String taskTitle;     // タスク名（例：説明会（11:00・オンライン））
        private String memo;          // 企業のメモ（日本語の条件・勤務地・特徴）
        private String jdText;        // 応募の求人票（解説・想定質問・締切抽出で使える）
        private boolean existing;     // 同じ名前の企業がすでに登録されているか（表示用）
        private List<String> existingPositions = new ArrayList<>(); // 登録済みの企業の応募の職種（表示用）

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIndustry() {
            return industry;
        }

        public void setIndustry(String industry) {
            this.industry = industry;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public LocalDate getEventDate() {
            return eventDate;
        }

        public void setEventDate(LocalDate eventDate) {
            this.eventDate = eventDate;
        }

        public String getTaskTitle() {
            return taskTitle;
        }

        public void setTaskTitle(String taskTitle) {
            this.taskTitle = taskTitle;
        }

        public String getMemo() {
            return memo;
        }

        public void setMemo(String memo) {
            this.memo = memo;
        }

        public String getJdText() {
            return jdText;
        }

        public void setJdText(String jdText) {
            this.jdText = jdText;
        }

        public boolean isExisting() {
            return existing;
        }

        public void setExisting(boolean existing) {
            this.existing = existing;
        }

        public List<String> getExistingPositions() {
            return existingPositions;
        }

        public void setExistingPositions(List<String> existingPositions) {
            this.existingPositions = existingPositions;
        }
    }
}
