package com.khang.jobtrack.service;

import com.khang.jobtrack.entity.Interview;
import com.khang.jobtrack.entity.PartTimeShift;
import com.khang.jobtrack.repository.InterviewRepository;
import com.khang.jobtrack.repository.PartTimeShiftRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khang が実装する3つのメソッドのテスト。
 * 実装前は赤（失敗）、正しく実装すると緑になる。
 */
@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    PartTimeShiftRepository shiftRepository;

    @Mock
    InterviewRepository interviewRepository;

    @InjectMocks
    ShiftService shiftService;

    // ---------- テスト用データを作る小さなヘルパー ----------

    private static LocalDateTime t(int month, int day, int hour) {
        return LocalDateTime.of(2026, month, day, hour, 0);
    }

    private static PartTimeShift shift(LocalDateTime start, LocalDateTime end) {
        PartTimeShift s = new PartTimeShift();
        s.setWorkplace("テスト店");
        s.setStartAt(start);
        s.setEndAt(end);
        return s;
    }

    private static Interview interview(LocalDateTime at) {
        Interview i = new Interview();
        i.setRound(1);
        i.setScheduledAt(at);
        return i;
    }

    // ---------- overlaps ----------

    @Test
    @DisplayName("overlaps: 一部が重なる → true")
    void overlaps_partial() {
        assertThat(ShiftService.overlaps(t(10, 20, 9), t(10, 20, 13), t(10, 20, 10), t(10, 20, 11))).isTrue();
        assertThat(ShiftService.overlaps(t(10, 20, 9), t(10, 20, 11), t(10, 20, 10), t(10, 20, 12))).isTrue();
    }

    @Test
    @DisplayName("overlaps: 片方がもう片方を完全に含む → true")
    void overlaps_contains() {
        assertThat(ShiftService.overlaps(t(10, 20, 10), t(10, 20, 11), t(10, 20, 9), t(10, 20, 13))).isTrue();
    }

    @Test
    @DisplayName("overlaps: 終了 = 相手の開始（境界が接するだけ）→ false")
    void overlaps_touching() {
        assertThat(ShiftService.overlaps(t(10, 20, 9), t(10, 20, 10), t(10, 20, 10), t(10, 20, 11))).isFalse();
        assertThat(ShiftService.overlaps(t(10, 20, 10), t(10, 20, 11), t(10, 20, 9), t(10, 20, 10))).isFalse();
    }

    @Test
    @DisplayName("overlaps: 完全に離れている → false")
    void overlaps_separate() {
        assertThat(ShiftService.overlaps(t(10, 20, 9), t(10, 20, 10), t(10, 20, 15), t(10, 20, 16))).isFalse();
    }

    // ---------- findConflicts ----------

    @Test
    @DisplayName("findConflicts: シフト 9-13時 と 10時の面接（1時間）→ 1件")
    void findConflicts_found() {
        PartTimeShift s1 = shift(t(10, 20, 9), t(10, 20, 13));
        PartTimeShift s2 = shift(t(10, 21, 9), t(10, 21, 13)); // 別の日 → 重ならない
        Interview i1 = interview(t(10, 20, 10));

        List<ScheduleConflict> result = shiftService.findConflicts(List.of(s1, s2), List.of(i1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).shift()).isSameAs(s1);
        assertThat(result.get(0).interview()).isSameAs(i1);
    }

    @Test
    @DisplayName("findConflicts: シフト終了 = 面接開始 → 重複なし")
    void findConflicts_touching() {
        PartTimeShift s1 = shift(t(10, 20, 9), t(10, 20, 13));
        Interview i1 = interview(t(10, 20, 13));

        assertThat(shiftService.findConflicts(List.of(s1), List.of(i1))).isEmpty();
    }

    // ---------- calcWeeklyHours ----------

    @Test
    @DisplayName("calcWeeklyHours: 5時間×5日 + 4時間 = 29時間 → 28時間超過")
    void calcWeeklyHours_over() {
        // 2026-11-02 は月曜日
        List<PartTimeShift> shifts = List.of(
                shift(t(11, 2, 17), t(11, 2, 22)),
                shift(t(11, 3, 17), t(11, 3, 22)),
                shift(t(11, 4, 17), t(11, 4, 22)),
                shift(t(11, 5, 17), t(11, 5, 22)),
                shift(t(11, 6, 17), t(11, 6, 22)),
                shift(t(11, 7, 12), t(11, 7, 16)),
                shift(t(11, 9, 12), t(11, 9, 14))  // 翌週（月曜）
        );

        List<WeeklyHours> result = shiftService.calcWeeklyHours(shifts);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).weekStart()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(result.get(0).hours()).isEqualTo(29.0);
        assertThat(result.get(0).over()).isTrue();
        assertThat(result.get(1).weekStart()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(result.get(1).hours()).isEqualTo(2.0);
        assertThat(result.get(1).over()).isFalse();
    }

    @Test
    @DisplayName("calcWeeklyHours: 日曜のシフトは前の月曜の週に入る")
    void calcWeeklyHours_sundayBelongsToPreviousWeek() {
        // 2026-11-08 は日曜日 → 週の始まりは 11-02
        List<WeeklyHours> result = shiftService.calcWeeklyHours(List.of(shift(t(11, 8, 10), t(11, 8, 13))));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).weekStart()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(result.get(0).hours()).isEqualTo(3.0);
    }
}
