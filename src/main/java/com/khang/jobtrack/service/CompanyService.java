package com.khang.jobtrack.service;

import com.khang.jobtrack.dto.CompanyForm;
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
        return companyRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId());
    }

    public boolean isNameTaken(User user, String name) {
        return companyRepository.existsByUserIdAndName(user.getId(), name);
    }

    public void create(User user, CompanyForm form) {
        Company company = new Company();

        company.setUser(user);
        company.setName(form.getName());
        company.setIndustry(form.getIndustry());
        company.setUrl(form.getUrl());
        company.setMemo(form.getMemo());

        companyRepository.save(company);
    }
}