package com.khang.jobtrack.controller;

import com.khang.jobtrack.service.CompanyService;
import com.khang.jobtrack.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import com.khang.jobtrack.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/companies")
public class CompanyController {

    private final UserService userService;
    private final CompanyService companyService;

    public CompanyController(UserService userService, CompanyService companyService) {
        this.userService = userService;
        this.companyService = companyService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("companies", companyService.findAllByUser(user));
        return "companies/list";
    }
}
