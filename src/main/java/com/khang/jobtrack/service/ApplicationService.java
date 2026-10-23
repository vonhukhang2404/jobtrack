package com.khang.jobtrack.service;

import com.khang.jobtrack.dto.ApplicationForm;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.ApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ApplicationService {
    private final ApplicationRepository applicationRepository;
    private final CompanyService companyService;

    public ApplicationService(ApplicationRepository applicationRepository, CompanyService companyService) {
        this.applicationRepository = applicationRepository;
        this.companyService = companyService;
    }

    public List<Application> findAllByUser(User user) {
        return applicationRepository.findByCompanyUserIdOrderByCreatedAtDescIdDesc(user.getId());
    }

    public Application findOwned(User user, Long id) {
        return applicationRepository.findByIdAndCompanyUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public void create(User user, ApplicationForm form) {
        Application application = new Application();

        application.setCompany(companyService.findOwned(user, form.getCompanyId()));
        application.setPosition(form.getPosition());
        application.setStatus(form.getStatus());
        application.setAppliedAt(form.getAppliedAt());
        application.setJdText(form.getJdText());

        applicationRepository.save(application);
    }

    public void update(User user, Long id, ApplicationForm form) {
        Application application = findOwned(user, id);

        application.setCompany(companyService.findOwned(user, form.getCompanyId()));
        application.setPosition(form.getPosition());
        application.setStatus(form.getStatus());
        application.setAppliedAt(form.getAppliedAt());
        application.setJdText(form.getJdText());

        applicationRepository.save(application);
    }

    public void delete(User user, Long id) {
        Application application = findOwned(user, id);

        applicationRepository.delete(application);
    }
}
