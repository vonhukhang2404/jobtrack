package com.khang.jobtrack.repository;

import com.khang.jobtrack.entity.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @EntityGraph(attributePaths = {"application", "application.company"})
    List<Task> findByApplicationCompanyUserIdOrderByDoneAscDueDateAscIdAsc(Long userId);

    Optional<Task> findByIdAndApplicationCompanyUserId(Long id, Long userId);

    // 取り込み用：同じ応募に同じ名前・同じ締切のタスクがあれば作らない
    boolean existsByApplicationIdAndTitleAndDueDate(Long applicationId, String title, LocalDate dueDate);

    @EntityGraph(attributePaths = {"application", "application.company"})
    List<Task> findByApplicationCompanyUserIdAndDoneFalseAndDueDateLessThanEqualOrderByDueDateAscIdAsc(
            Long userId, LocalDate limit);
}
