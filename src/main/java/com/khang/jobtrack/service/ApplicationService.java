package com.khang.jobtrack.service;

import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.ApplicationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApplicationService {
    private final ApplicationRepository applicationRepository;

    ApplicationService(ApplicationRepository applicationRepository){
        this.applicationRepository = applicationRepository;
    }

    public List<Application> findAllByUser(User user) {

        return applicationRepository.findByCompanyUserIdOrderByCreatedAtDescIdDesc(user.getId());
    }
}
