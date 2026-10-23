package com.khang.jobtrack.controller;

//   ApplicationService, UserService, User,
//   Controller, Model (org.springframework.ui!),
//   AuthenticationPrincipal, UserDetails,
//   GetMapping, RequestMapping

import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.ApplicationService;
import com.khang.jobtrack.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/applications")
public class ApplicationController {
    private final UserService userService;
    private final ApplicationService applicationService;

    public ApplicationController(UserService userService, ApplicationService applicationService) {
        this.userService = userService;
        this.applicationService = applicationService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("applications", applicationService.findAllByUser(user));
        return "applications/list";
    }
}