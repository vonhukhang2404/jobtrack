package com.khang.jobtrack.controller;

import com.khang.jobtrack.dto.TaskForm;
import com.khang.jobtrack.entity.Task;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.ApplicationService;
import com.khang.jobtrack.service.TaskService;
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

import java.time.LocalDate;

@Controller
@RequestMapping("/tasks")
public class TaskController {
    private final UserService userService;
    private final ApplicationService applicationService;
    private final TaskService taskService;

    public TaskController(UserService userService, ApplicationService applicationService, TaskService taskService) {
        this.userService = userService;
        this.applicationService = applicationService;
        this.taskService = taskService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("tasks", taskService.findAllByUser(user));
        return "tasks/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("taskForm", new TaskForm());
        addFormOptions(model, user);
        return "tasks/form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal UserDetails userDetails,
                         @Valid @ModelAttribute TaskForm taskForm,
                         BindingResult bindingResult, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());

        if (bindingResult.hasErrors()) {
            addFormOptions(model, user);
            return "tasks/form";
        }
        taskService.create(user, taskForm);
        return "redirect:/tasks";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal UserDetails userDetails,
                           @PathVariable Long id,
                           Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        Task task = taskService.findOwned(user, id);

        TaskForm form = new TaskForm();
        form.setApplicationId(task.getApplication().getId());
        form.setTitle(task.getTitle());
        form.setDueDate(task.getDueDate());
        form.setDone(task.isDone());

        model.addAttribute("taskForm", form);
        model.addAttribute("taskId", id);
        addFormOptions(model, user);
        return "tasks/form";
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal UserDetails userDetails,
                         @PathVariable Long id,
                         @Valid @ModelAttribute TaskForm taskForm,
                         BindingResult bindingResult,
                         Model model) {
        User user = userService.findByEmail(userDetails.getUsername());

        if (bindingResult.hasErrors()) {
            model.addAttribute("taskId", id);
            addFormOptions(model, user);
            return "tasks/form";
        }
        taskService.update(user, id, taskForm);

        return "redirect:/tasks";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal UserDetails userDetails,
                         @PathVariable Long id) {
        User user = userService.findByEmail(userDetails.getUsername());

        taskService.delete(user, id);

        return "redirect:/tasks";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@AuthenticationPrincipal UserDetails userDetails,
                         @PathVariable Long id) {
        User user = userService.findByEmail(userDetails.getUsername());

        taskService.toggle(user, id);
        return "redirect:/tasks";
    }

    private void addFormOptions(Model model, User user) {
        model.addAttribute("applications", applicationService.findAllByUser(user));
    }
}
