package com.khang.jobtrack.service;

import com.khang.jobtrack.entity.Company;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public List<Company> findAllByUser(User user) {
        return companyRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }
}