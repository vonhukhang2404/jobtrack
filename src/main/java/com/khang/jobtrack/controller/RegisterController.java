package com.khang.jobtrack.controller;

import com.khang.jobtrack.dto.RegisterForm;
import com.khang.jobtrack.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class RegisterController {

    private final UserService userService;

    public RegisterController(UserService userService) {
        this.userService = userService;
    }

    // Hiện form đăng ký
    @GetMapping("/register")
    public String showForm(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "register";
    }

    // Xử lý khi bấm nút 登録
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterForm registerForm,
                           BindingResult result) {

        if (userService.isEmailTaken(registerForm.getEmail())) {
            result.rejectValue("email", "duplicate", "このメールアドレスは既に登録されています");
        }

        if (result.hasErrors()) {
            return "register";
        }

        userService.register(registerForm);
        return "redirect:/login?registered";
    }
}