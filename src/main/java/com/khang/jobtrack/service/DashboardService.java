package com.khang.jobtrack.service;

import com.khang.jobtrack.entity.ApplicationStatus;
import com.khang.jobtrack.entity.Task;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.ApplicationRepository;
import com.khang.jobtrack.repository.CompanyRepository;
import com.khang.jobtrack.repository.StatusCount;
import com.khang.jobtrack.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final TaskRepository taskRepository;

    public DashboardService(CompanyRepository companyRepository, ApplicationRepository applicationRepository, TaskRepository taskRepository) {
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.taskRepository = taskRepository;
    }

    public long countCompanies(User user) {
        return companyRepository.countByUserId(user.getId());
    }

    public long countApplications(User user) {
        return applicationRepository.countByCompanyUserId(user.getId());
    }

    public List<Task> findUpcomingTasks(User user, LocalDate today) {
        LocalDate limit = today.plusDays(7);
        return taskRepository.findByApplicationCompanyUserIdAndDoneFalseAndDueDateLessThanEqualOrderByDueDateAscIdAsc(user.getId(), limit);
    }

    public Map<ApplicationStatus, Long> countByStatus(User user) {
        Map<ApplicationStatus, Long> result = new EnumMap<>(ApplicationStatus.class);
        // ① Đặt cả 9 trạng thái = 0
        for (ApplicationStatus status : ApplicationStatus.values()) {

            result.put(status, 0L);
        }

        // ② Ghi đè số đếm thật từ DB
        for (StatusCount sc : applicationRepository.countByStatus(user.getId())) {
            result.put(sc.getStatus(), sc.getCount());
        }

        return result;
    }
}