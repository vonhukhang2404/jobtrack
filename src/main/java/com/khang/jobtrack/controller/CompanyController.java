package com.khang.jobtrack.controller;

import com.khang.jobtrack.dto.CompanyForm;
import com.khang.jobtrack.service.CompanyService;
import com.khang.jobtrack.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import com.khang.jobtrack.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/companies")
public class CompanyController {

    private final UserService userService;
    private final CompanyService companyService;

    public CompanyController(UserService userService, CompanyService companyService) {
        this.userService = userService;
        this.companyService = companyService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("companies", companyService.findAllByUser(user));
        return "companies/list";
    }

    @GetMapping("/new")                         // GET /companies/new → hiện form
    public String newForm(Model model) {

        model.addAttribute("companyForm", new CompanyForm());
        return "companies/form";
    }

    @PostMapping                                // POST /companies → lưu
    public String create(@AuthenticationPrincipal UserDetails userDetails,
                         @Valid @ModelAttribute CompanyForm companyForm,
                         BindingResult bindingResult) {
        User user = userService.findByEmail(userDetails.getUsername());

        if (companyService.isNameTaken(user, companyForm.getName())) {
            bindingResult.rejectValue("name", "duplicate", "この企業は既に登録されています");
        }

        if (bindingResult.hasErrors()) {
            return "companies/form";
        }
        companyService.create(user, companyForm);

        return "redirect:/companies";
    }
}
