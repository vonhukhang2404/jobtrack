package com.khang.jobtrack.controller;

import com.khang.jobtrack.dto.InterviewForm;
import com.khang.jobtrack.entity.Interview;
import com.khang.jobtrack.entity.InterviewFormat;
import com.khang.jobtrack.entity.InterviewResult;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.ApplicationService;
import com.khang.jobtrack.service.InterviewService;
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

import java.time.LocalDateTime;

@Controller
@RequestMapping("/interviews")
public class InterviewController {
    private static final String DUPLICATE_MESSAGE = "この応募の面接は既に登録されています";

    private final UserService userService;
    private final ApplicationService applicationService;
    private final InterviewService interviewService;

    public InterviewController(UserService userService, ApplicationService applicationService,
                               InterviewService interviewService) {
        this.userService = userService;
        this.applicationService = applicationService;
        this.interviewService = interviewService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("interviews", interviewService.findAllByUser(user));
        model.addAttribute("now", LocalDateTime.now());
        return "interviews/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("interviewForm", new InterviewForm());
        addFormOptions(model, user);
        return "interviews/form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal UserDetails userDetails,
                         @Valid @ModelAttribute InterviewForm interviewForm,
                         BindingResult bindingResult, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());

        if (canCheckDuplicate(interviewForm, bindingResult)
                && interviewService.isRoundTaken(user, interviewForm.getApplicationId(), interviewForm.getRound())) {
            bindingResult.rejectValue("round", "duplicate", DUPLICATE_MESSAGE);
        }
        if (bindingResult.hasErrors()) {
            addFormOptions(model, user);
            return "interviews/form";
        }
        interviewService.create(user, interviewForm);
        return "redirect:/interviews";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal UserDetails userDetails,
                           @PathVariable Long id, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        Interview interview = interviewService.findOwned(user, id);

        InterviewForm form = new InterviewForm();
        form.setApplicationId(interview.getApplication().getId());
        form.setRound(interview.getRound());
        form.setScheduledAt(interview.getScheduledAt());
        form.setFormat(interview.getFormat());
        form.setResult(interview.getResult());
        form.setNotes(interview.getNotes());

        model.addAttribute("interviewForm", form);
        model.addAttribute("interviewId", id);
        addFormOptions(model, user);
        return "interviews/form";
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal UserDetails userDetails,
                         @PathVariable Long id,
                         @Valid @ModelAttribute InterviewForm interviewForm,
                         BindingResult bindingResult, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());

        if (canCheckDuplicate(interviewForm, bindingResult)
                && interviewService.isRoundTakenByOther(user, interviewForm.getApplicationId(),
                interviewForm.getRound(), id)) {
            bindingResult.rejectValue("round", "duplicate", DUPLICATE_MESSAGE);
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("interviewId", id);
            addFormOptions(model, user);
            return "interviews/form";
        }
        interviewService.update(user, id, interviewForm);
        return "redirect:/interviews";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        User user = userService.findByEmail(userDetails.getUsername());
        interviewService.delete(user, id);
        return "redirect:/interviews";
    }

    // 応募と回に入力エラーがないときだけ重複チェックする
    private boolean canCheckDuplicate(InterviewForm form, BindingResult bindingResult) {
        return form.getApplicationId() != null && form.getRound() != null
                && !bindingResult.hasFieldErrors("applicationId") && !bindingResult.hasFieldErrors("round");
    }

    private void addFormOptions(Model model, User user) {
        model.addAttribute("applications", applicationService.findAllByUser(user));
        model.addAttribute("formats", InterviewFormat.values());
        model.addAttribute("results", InterviewResult.values());
    }
}
