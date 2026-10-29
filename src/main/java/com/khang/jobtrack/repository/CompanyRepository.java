package com.khang.jobtrack.repository;

import com.khang.jobtrack.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    // Danh sách công ty của 1 user, mới nhất lên đầu
    List<Company> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Company> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

    long countByUserId(Long userId);
}