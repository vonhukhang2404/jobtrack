package com.khang.jobtrack.repository;

import com.khang.jobtrack.entity.AiResult;
import com.khang.jobtrack.entity.AiResultType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiResultRepository extends JpaRepository<AiResult, Long> {

    // 同じ応募・同じ種類・同じ入力の結果のうち、最新の1件
    Optional<AiResult> findFirstByApplicationIdAndTypeAndInputTextOrderByIdDesc(
            Long applicationId, AiResultType type, String inputText);
}
