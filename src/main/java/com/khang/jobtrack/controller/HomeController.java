package com.khang.jobtrack.controller;

import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.DashboardService;
import com.khang.jobtrack.service.InterviewService;
import com.khang.jobtrack.service.ShiftService;
import com.khang.jobtrack.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
public class HomeController {
    private final UserService userService;
    private final DashboardService dashboardService;
    private final InterviewService interviewService;
    private final ShiftService shiftService;

    public HomeController(UserService userService, DashboardService dashboardService,
                          InterviewService interviewService, ShiftService shiftService) {
        this.userService = userService;
        this.dashboardService = dashboardService;
        this.interviewService = interviewService;
        this.shiftService = shiftService;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        LocalDate today = LocalDate.now();

        model.addAttribute("companyCount", dashboardService.countCompanies(user));
        model.addAttribute("applicationCount", dashboardService.countApplications(user));
        model.addAttribute("upcomingTasks", dashboardService.findUpcomingTasks(user, today));
        model.addAttribute("statusCounts", dashboardService.countByStatus(user));
        model.addAttribute("today", today);

        // 週4：今後の面接・シフト重複の件数
        model.addAttribute("upcomingInterviews", interviewService.findUpcoming(user, LocalDateTime.now()));
        model.addAttribute("conflictCount", shiftService.findConflicts(user).size());

        return "index";
    }
}
