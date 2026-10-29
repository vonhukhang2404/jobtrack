package com.khang.jobtrack.service;

import com.khang.jobtrack.dto.TaskForm;
import com.khang.jobtrack.entity.Task;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    TaskRepository taskRepository;

    @Mock
    ApplicationService applicationService;

    @InjectMocks
    TaskService taskService;

    @Test
    @DisplayName("toggle: 未完了のタスクを完了にして保存する")
    void toggle_changesUndoneToDone() {
        // Given: 未完了のタスクが存在する
        User user = new User();
        Task task = new Task();
        task.setDone(false);
        when(taskRepository.findByIdAndApplicationCompanyUserId(1L, user.getId()))
                .thenReturn(Optional.of(task));

        // When
        taskService.toggle(user, 1L);

        // Then: 完了になり、保存されている
        assertThat(task.isDone()).isTrue();
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("findOwned: 他人のタスク（見つからない）は404")
    void findOwned_otherUsersTask_throws404() {
        // Given: 検索しても見つからない
        User user = new User();
        when(taskRepository.findByIdAndApplicationCompanyUserId(99L, user.getId()))
                .thenReturn(Optional.empty());

        // When + Then: 404の例外が発生する
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> taskService.findOwned(user, 99L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("create: 他人の応募IDでは404になり、保存しない")
    void create_otherUsersApplication_throws404AndDoesNotSave() {
        User user = new User();
        TaskForm taskForm = new TaskForm();
        taskForm.setApplicationId(1L);
        when(applicationService.findOwned(user, 1L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThrows(ResponseStatusException.class,
                () -> taskService.create(user, taskForm));

        verify(taskRepository, never()).save(any());
    }
}