package com.khang.jobtrack.controller;

import com.khang.jobtrack.dto.ShiftForm;
import com.khang.jobtrack.entity.PartTimeShift;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.ScheduleConflict;
import com.khang.jobtrack.service.ShiftService;
import com.khang.jobtrack.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/shifts")
public class ShiftController {

    private final UserService userService;
    private final ShiftService shiftService;

    public ShiftController(UserService userService, ShiftService shiftService) {
        this.userService = userService;
        this.shiftService = shiftService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        List<ScheduleConflict> conflicts = shiftService.findConflicts(user);
        // 一覧で「面接と重複」バッジを出すシフトのID
        Set<Long> conflictShiftIds = conflicts.stream()
                .map(c -> c.shift().getId())
                .collect(Collectors.toSet());

        model.addAttribute("shifts", shiftService.findAllByUser(user));
        model.addAttribute("conflicts", conflicts);
        model.addAttribute("conflictShiftIds", conflictShiftIds);
        model.addAttribute("weeklyHours", shiftService.calcWeeklyHours(user));
        return "shifts/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("shiftForm", new ShiftForm());
        return "shifts/form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal UserDetails userDetails,
                         @Valid @ModelAttribute ShiftForm shiftForm,
                         BindingResult bindingResult) {
        checkOrder(shiftForm, bindingResult);
        if (bindingResult.hasErrors()) {
            return "shifts/form";
        }
        User user = userService.findByEmail(userDetails.getUsername());
        shiftService.create(user, shiftForm);
        return "redirect:/shifts";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal UserDetails userDetails,
                           @PathVariable Long id, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        PartTimeShift shift = shiftService.findOwned(user, id);

        ShiftForm form = new ShiftForm();
        form.setWorkplace(shift.getWorkplace());
        form.setStartAt(shift.getStartAt());
        form.setEndAt(shift.getEndAt());
        form.setMemo(shift.getMemo());

        model.addAttribute("shiftForm", form);
        model.addAttribute("shiftId", id);
        return "shifts/form";
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal UserDetails userDetails,
                         @PathVariable Long id,
                         @Valid @ModelAttribute ShiftForm shiftForm,
                         BindingResult bindingResult, Model model) {
        checkOrder(shiftForm, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("shiftId", id);
            return "shifts/form";
        }
        User user = userService.findByEmail(userDetails.getUsername());
        shiftService.update(user, id, shiftForm);
        return "redirect:/shifts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        User user = userService.findByEmail(userDetails.getUsername());
        shiftService.delete(user, id);
        return "redirect:/shifts";
    }

    // 終了は開始より後（両方入力済みのときだけチェック）
    private void checkOrder(ShiftForm form, BindingResult bindingResult) {
        if (form.getStartAt() != null && form.getEndAt() != null
                && !form.getEndAt().isAfter(form.getStartAt())) {
            bindingResult.rejectValue("endAt", "order", "終了は開始より後にしてください");
        }
    }
}
