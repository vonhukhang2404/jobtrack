package com.khang.jobtrack.repository;

import com.khang.jobtrack.entity.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @EntityGraph(attributePaths = {"application", "application.company"})
    List<Task> findByApplicationCompanyUserIdOrderByDoneAscDueDateAscIdAsc(Long userId);

    Optional<Task> findByIdAndApplicationCompanyUserId(Long id, Long userId);
}
