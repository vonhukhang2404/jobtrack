package com.khang.jobtrack.service;

import com.khang.jobtrack.dto.TaskForm;
import com.khang.jobtrack.entity.Task;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final ApplicationService applicationService;

    public TaskService(TaskRepository taskRepository, ApplicationService applicationService) {
        this.taskRepository = taskRepository;
        this.applicationService = applicationService;
    }

    public List<Task> findAllByUser(User user) {
        return taskRepository.findByApplicationCompanyUserIdOrderByDoneAscDueDateAscIdAsc(user.getId());
    }

    public Task findOwned(User user, Long id) {
        return taskRepository.findByIdAndApplicationCompanyUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public void create(User user, TaskForm form) {
        Task task = new Task();

        task.setApplication(applicationService.findOwned(user, form.getApplicationId()));
        task.setTitle(form.getTitle());
        task.setDueDate(form.getDueDate());
        task.setDone(form.isDone());

        taskRepository.save(task);
    }

    public void update(User user, Long id, TaskForm form) {
        Task task = findOwned(user, id);

        task.setApplication(applicationService.findOwned(user, form.getApplicationId()));
        task.setTitle(form.getTitle());
        task.setDueDate(form.getDueDate());
        task.setDone(form.isDone());

        taskRepository.save(task);
    }

    public void delete(User user, Long id) {
        Task task = findOwned(user, id);

        taskRepository.delete(task);
    }

    public void toggle(User user, Long id) {
        Task task = findOwned(user, id);

        task.setDone(!task.isDone());
        
        taskRepository.save(task);
    }
}
