package com.khang.jobtrack.service;

import com.khang.jobtrack.dto.CompanyForm;
import com.khang.jobtrack.entity.Company;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.CompanyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public Company findOwned(User user, Long id) {
        return companyRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public boolean isNameTakenByOther(User user, String name, Long id) {
        return companyRepository.existsByUserIdAndNameAndIdNot(user.getId(), name, id);
    }

    public void update(User user, Long id, CompanyForm form) {
        Company company = findOwned(user, id);
        company.setName(form.getName());
        company.setIndustry(form.getIndustry());
        company.setUrl(form.getUrl());
        company.setMemo(form.getMemo());

        companyRepository.save(company);
    }

    public void delete(User user, Long id) {
        Company company = findOwned(user, id);

        companyRepository.delete(company);
    }
}