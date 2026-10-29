package com.khang.jobtrack.service;

import com.khang.jobtrack.entity.ApplicationStatus;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.ApplicationRepository;
import com.khang.jobtrack.repository.CompanyRepository;
import com.khang.jobtrack.repository.StatusCount;
import com.khang.jobtrack.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    CompanyRepository companyRepository;

    @Mock
    ApplicationRepository applicationRepository;

    @Mock
    TaskRepository taskRepository;

    @InjectMocks
    DashboardService dashboardService;

    @Test
    @DisplayName("countByStatus: DBにないステータスも0で埋め、9件すべて返す")
    void countByStatus_fillsMissingStatusesWithZero() {
        // Given: DBには「エントリー 2件」だけがある
        User user = new User();
        StatusCount entry = mock(StatusCount.class);
        when(entry.getStatus()).thenReturn(ApplicationStatus.ENTRY);
        when(entry.getCount()).thenReturn(2L);
        when(applicationRepository.countByStatus(user.getId())).thenReturn(List.of(entry));

        // When
        Map<ApplicationStatus, Long> result = dashboardService.countByStatus(user);

        // Then
        assertThat(result).hasSize(9);
        assertThat(result.get(ApplicationStatus.ENTRY)).isEqualTo(2L);
        assertThat(result.get(ApplicationStatus.OFFER)).isEqualTo(0L);
        assertThat(result.keySet()).containsExactly(ApplicationStatus.values());
    }
}