package com.khang.jobtrack.repository;

import com.khang.jobtrack.entity.Interview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    // 一覧：新しい順
    @EntityGraph(attributePaths = {"application", "application.company"})
    List<Interview> findByApplicationCompanyUserIdOrderByScheduledAtDescIdDesc(Long userId);

    // 1件取得（他人のデータは取得できない）
    Optional<Interview> findByIdAndApplicationCompanyUserId(Long id, Long userId);

    // 同じ応募で同じ回の面接があるか（登録用・更新用）
    boolean existsByApplicationIdAndRound(Long applicationId, int round);

    boolean existsByApplicationIdAndRoundAndIdNot(Long applicationId, int round, Long id);

    // ダッシュボード用：これからの面接（古い順）
    @EntityGraph(attributePaths = {"application", "application.company"})
    List<Interview> findByApplicationCompanyUserIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(
            Long userId, LocalDateTime from);
}
