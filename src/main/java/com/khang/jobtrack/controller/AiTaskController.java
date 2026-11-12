package com.khang.jobtrack.controller;

import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.TaskSuggestion;
import com.khang.jobtrack.dto.AiTaskForm;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.AiTaskService;
import com.khang.jobtrack.service.ApplicationService;
import com.khang.jobtrack.service.UserService;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * 求人票 → AIで締切を抽出 → 確認 → タスク登録
 *   GET  /applications/{id}/ai-tasks          求人票と「AIで抽出」ボタン
 * ※ Model名を "application" にすると Thymeleaf の予約語（ServletContext）と衝突するので "jobApplication"
 *   POST /applications/{id}/ai-tasks/extract  AIを呼んで候補を表示
 *   POST /applications/{id}/ai-tasks          チェックした候補をタスクとして保存
 */
@Controller
@RequestMapping("/applications/{id}/ai-tasks")
public class AiTaskController {

    private final UserService userService;
    private final ApplicationService applicationService;
    private final AiTaskService aiTaskService;

    public AiTaskController(UserService userService, ApplicationService applicationService,
                            AiTaskService aiTaskService) {
        this.userService = userService;
        this.applicationService = applicationService;
        this.aiTaskService = aiTaskService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String show(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("jobApplication", applicationService.findOwned(user, id));
        return "ai/tasks";
    }

    @PostMapping("/extract")
    public String extract(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("jobApplication", applicationService.findOwned(user, id));

        try {
            List<TaskSuggestion> suggestions = aiTaskService.suggest(user, id, LocalDate.now());
            AiTaskForm form = new AiTaskForm();
            for (TaskSuggestion s : suggestions) {
                form.getItems().add(new AiTaskForm.Item(s.title(), s.dueDate()));
            }
            model.addAttribute("aiTaskForm", form);
        } catch (AiException e) {
            model.addAttribute("aiError", e.getMessage());
        }
        return "ai/tasks";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id,
                       @ModelAttribute AiTaskForm aiTaskForm, BindingResult bindingResult,
                       Model model, RedirectAttributes redirectAttributes) {
        User user = userService.findByEmail(userDetails.getUsername());

        // チェックした行だけ入力チェック（チェックしていない行は空でもよい）
        List<AiTaskForm.Item> items = aiTaskForm.getItems();
        for (int i = 0; i < items.size(); i++) {
            AiTaskForm.Item item = items.get(i);
            if (!item.isSelected()) {
                continue;
            }
            if (item.getTitle() == null) {
                bindingResult.rejectValue("items[" + i + "].title", "required", "タスクを入力してください");
            } else if (item.getTitle().length() > 100) {
                bindingResult.rejectValue("items[" + i + "].title", "size", "100文字以内で入力してください");
            }
            if (item.getDueDate() == null) {
                bindingResult.rejectValue("items[" + i + "].dueDate", "required", "締切を入力してください");
            }
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("jobApplication", applicationService.findOwned(user, id));
            return "ai/tasks";
        }

        int count = aiTaskService.saveSelected(user, id, aiTaskForm);
        redirectAttributes.addFlashAttribute("message", "AIの提案から " + count + " 件のタスクを登録しました。");
        return "redirect:/tasks";
    }
}
