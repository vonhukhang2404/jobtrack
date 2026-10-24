package com.khang.jobtrack.controller;

import com.khang.jobtrack.dto.ApplicationForm;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.ApplicationStatus;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.ApplicationService;
import com.khang.jobtrack.service.CompanyService;
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

@Controller
@RequestMapping("/applications")
public class ApplicationController {
    private final UserService userService;
    private final ApplicationService applicationService;
    private final CompanyService companyService;

    public ApplicationController(UserService userService, ApplicationService applicationService, CompanyService companyService) {
        this.userService = userService;
        this.applicationService = applicationService;
        this.companyService = companyService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("applications", applicationService.findAllByUser(user));
        return "applications/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("applicationForm", new ApplicationForm());
        addFormOptions(model, user);
        return "applications/form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal UserDetails userDetails,
                         @Valid @ModelAttribute ApplicationForm applicationForm,
                         BindingResult bindingResult, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());

        if (bindingResult.hasErrors()) {
            addFormOptions(model, user);
            return "applications/form";
        }
        applicationService.create(user, applicationForm);
        return "redirect:/applications";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal UserDetails userDetails,
                           @PathVariable Long id,
                           Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        Application application = applicationService.findOwned(user, id);

        ApplicationForm form = new ApplicationForm();
        form.setCompanyId(application.getCompany().getId());
        form.setPosition(application.getPosition());
        form.setStatus(application.getStatus());
        form.setAppliedAt(application.getAppliedAt());
        form.setJdText(application.getJdText());

        model.addAttribute("applicationForm", form);
        model.addAttribute("applicationId", id);
        addFormOptions(model, user);
        return "applications/form";
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal UserDetails userDetails,
                         @PathVariable Long id,
                         @Valid @ModelAttribute ApplicationForm applicationForm,
                         BindingResult bindingResult,
                         Model model) {
        User user = userService.findByEmail(userDetails.getUsername());

        if (bindingResult.hasErrors()) {
            model.addAttribute("applicationId", id);
            addFormOptions(model, user);
            return "applications/form";
        }
        applicationService.update(user, id, applicationForm);

        return "redirect:/applications";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal UserDetails userDetails,
                         @PathVariable Long id) {
        User user = userService.findByEmail(userDetails.getUsername());

        applicationService.delete(user, id);

        return "redirect:/applications";
    }

    private void addFormOptions(Model model, User user) {
        model.addAttribute("companies", companyService.findAllByUser(user));
        model.addAttribute("statuses", ApplicationStatus.values());
    }
}