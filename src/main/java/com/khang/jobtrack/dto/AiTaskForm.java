package com.khang.jobtrack.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** AI確認画面のフォーム：候補ごとに「登録する／しない」とタイトル・締切を修正できる */
public class AiTaskForm {

    private List<Item> items = new ArrayList<>();

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static class Item {
        private boolean selected = true;
        private String title;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dueDate;

        public Item() {}

        public Item(String title, LocalDate dueDate) {
            this.title = title;
            this.dueDate = dueDate;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public LocalDate getDueDate() {
            return dueDate;
        }

        public void setDueDate(LocalDate dueDate) {
            this.dueDate = dueDate;
        }
    }
}
