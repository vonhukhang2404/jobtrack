package com.khang.jobtrack.controller;

import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.SummaryLanguage;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.ApplicationService;
import com.khang.jobtrack.service.JdSummaryService;
import com.khang.jobtrack.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 求人票の解説（やさしい日本語 / ベトナム語）
 *   GET  /applications/{id}/ai-summary   求人票と言語の選択
 *   POST /applications/{id}/ai-summary   AIで解説を作って表示（保存はしない。結果は ai_results にキャッシュ）
 */
@Controller
@RequestMapping("/applications/{id}/ai-summary")
public class AiSummaryController {

    private final UserService userService;
    private final ApplicationService applicationService;
    private final JdSummaryService jdSummaryService;

    public AiSummaryController(UserService userService, ApplicationService applicationService,
                               JdSummaryService jdSummaryService) {
        this.userService = userService;
        this.applicationService = applicationService;
        this.jdSummaryService = jdSummaryService;
    }

    @GetMapping
    public String show(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("jobApplication", applicationService.findOwned(user, id));
        model.addAttribute("languages", SummaryLanguage.values());
        return "ai/summary";
    }

    @PostMapping
    public String explain(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id,
                          @RequestParam SummaryLanguage language, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("jobApplication", applicationService.findOwned(user, id));
        model.addAttribute("languages", SummaryLanguage.values());
        model.addAttribute("selectedLanguage", language);

        try {
            model.addAttribute("summary", jdSummaryService.explain(user, id, language));
        } catch (AiException e) {
            model.addAttribute("aiError", e.getMessage());
        }
        return "ai/summary";
    }
}
