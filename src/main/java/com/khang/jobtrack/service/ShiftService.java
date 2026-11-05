package com.khang.jobtrack.service;

import com.khang.jobtrack.dto.ShiftForm;
import com.khang.jobtrack.entity.Interview;
import com.khang.jobtrack.entity.PartTimeShift;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.InterviewRepository;
import com.khang.jobtrack.repository.PartTimeShiftRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ShiftService {

    /**
     * 面接は開始時刻しか持たないので、1時間と見なす
     */
    public static final Duration INTERVIEW_DURATION = Duration.ofHours(1);

    private final PartTimeShiftRepository shiftRepository;
    private final InterviewRepository interviewRepository;

    public ShiftService(PartTimeShiftRepository shiftRepository, InterviewRepository interviewRepository) {
        this.shiftRepository = shiftRepository;
        this.interviewRepository = interviewRepository;
    }

    // ===================================================================
    // CRUD
    // ===================================================================

    public List<PartTimeShift> findAllByUser(User user) {
        return shiftRepository.findByUserIdOrderByStartAtAscIdAsc(user.getId());
    }

    public PartTimeShift findOwned(User user, Long id) {
        return shiftRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public void create(User user, ShiftForm form) {
        PartTimeShift shift = new PartTimeShift();
        shift.setUser(user);
        applyForm(shift, form);
        shiftRepository.save(shift);
    }

    public void update(User user, Long id, ShiftForm form) {
        PartTimeShift shift = findOwned(user, id);
        applyForm(shift, form);
        shiftRepository.save(shift);
    }

    public void delete(User user, Long id) {
        PartTimeShift shift = findOwned(user, id);
        shiftRepository.delete(shift);
    }

    private void applyForm(PartTimeShift shift, ShiftForm form) {
        shift.setWorkplace(form.getWorkplace());
        shift.setStartAt(form.getStartAt());
        shift.setEndAt(form.getEndAt());
        shift.setMemo(form.getMemo());
    }

    // ===================================================================
    // 警告（DBからデータを読み込んで、下の計算メソッドに渡す）
    // ===================================================================

    public List<ScheduleConflict> findConflicts(User user) {
        List<PartTimeShift> shifts = findAllByUser(user);
        List<Interview> interviews =
                interviewRepository.findByApplicationCompanyUserIdOrderByScheduledAtDescIdDesc(user.getId());
        return findConflicts(shifts, interviews);
    }

    public List<WeeklyHours> calcWeeklyHours(User user) {
        return calcWeeklyHours(findAllByUser(user));
    }

    // ===================================================================
    // ★ ここから Khang が実装する部分（ShiftServiceTest が緑になればOK）
    // ===================================================================

    /**
     * 2つの時間帯 [aStart, aEnd) と [bStart, bEnd) が重なっているか。
     * 「終了 = 相手の開始」（例：10:00〜11:00 と 11:00〜12:00）は重なっていない、とする。
     */
    public static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd,
                                   LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    /**
     * シフトと面接の重複をすべて探す。
     * 面接の終了時刻 = 開始 + INTERVIEW_DURATION
     */
    public List<ScheduleConflict> findConflicts(List<PartTimeShift> shifts, List<Interview> interviews) {
        List<ScheduleConflict> result = new ArrayList<>();
        for (PartTimeShift shift : shifts) {
            for (Interview interview : interviews) {
                LocalDateTime iStart = interview.getScheduledAt();
                LocalDateTime iEnd = iStart.plus(INTERVIEW_DURATION);

                if (overlaps(iStart, iEnd, shift.getStartAt(), shift.getEndAt())) {
                    result.add(new ScheduleConflict(shift, interview));
                }
            }
        }
        return result;
    }

    /**
     * 週（月曜始まり）ごとの勤務時間の合計を、週の古い順で返す。
     * ヒント：
     * 週の月曜日：shift.getStartAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
     * 時間     ：Duration.between(shift.getStartAt(), shift.getEndAt()).toMinutes() / 60.0
     * 集計     ：TreeMap<LocalDate, Double>（キーの順に並ぶ）＋ merge(key, hours, Double::sum)
     */
    public List<WeeklyHours> calcWeeklyHours(List<PartTimeShift> shifts) {
        Map<LocalDate, Double> totals = new TreeMap<>();
        for(PartTimeShift shift : shifts){
            LocalDate weekStart = shift.getStartAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            double hours = Duration.between(shift.getStartAt(), shift.getEndAt()).toMinutes() / 60.0;
            totals.merge(weekStart, hours, Double::sum);
        }

        List<WeeklyHours> result = new ArrayList<>();
        for (Map.Entry<LocalDate, Double> entry : totals.entrySet() ){
            result.add(new WeeklyHours(entry.getKey(),entry.getValue()));
        }
        return result;
    }
}
