package com.khang.jobtrack.controller;

import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.DashboardService;
import com.khang.jobtrack.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

@Controller
public class HomeController {
    private final UserService userService;
    private final DashboardService dashboardService;

    public HomeController(UserService userService, DashboardService dashboardService) {
        this.userService = userService;
        this.dashboardService = dashboardService;
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

        return "index";
    }
}