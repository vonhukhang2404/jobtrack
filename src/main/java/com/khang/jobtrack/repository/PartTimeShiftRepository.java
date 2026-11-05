package com.khang.jobtrack.repository;

import com.khang.jobtrack.entity.PartTimeShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartTimeShiftRepository extends JpaRepository<PartTimeShift, Long> {

    // 一覧：開始が早い順
    List<PartTimeShift> findByUserIdOrderByStartAtAscIdAsc(Long userId);

    Optional<PartTimeShift> findByIdAndUserId(Long id, Long userId);
}
