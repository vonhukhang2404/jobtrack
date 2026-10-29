package com.khang.jobtrack.repository;

import com.khang.jobtrack.entity.Application;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    @EntityGraph(attributePaths = "company")
    List<Application> findByCompanyUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Application> findByIdAndCompanyUserId(Long id, Long userId);

    long countByCompanyUserId(Long userId);

    @Query("""
            SELECT a.status AS status, COUNT(a) AS count
            FROM Application a
            WHERE a.company.user.id = :userId
            GROUP BY a.status
            """)
    List<StatusCount> countByStatus(@Param("userId") Long userId);
}