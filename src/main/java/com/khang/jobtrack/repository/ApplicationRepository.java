package com.khang.jobtrack.repository;

import com.khang.jobtrack.entity.Application;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    @EntityGraph(attributePaths = "company")
    List<Application> findByCompanyUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Application> findByIdAndCompanyUserId(Long id, Long userId);
}