package com.khang.jobtrack.service;

import com.khang.jobtrack.dto.InterviewForm;
import com.khang.jobtrack.entity.Interview;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.InterviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewService {
    private final InterviewRepository interviewRepository;
    private final ApplicationService applicationService;

    public InterviewService(InterviewRepository interviewRepository, ApplicationService applicationService) {
        this.interviewRepository = interviewRepository;
        this.applicationService = applicationService;
    }

    public List<Interview> findAllByUser(User user) {
        return interviewRepository.findByApplicationCompanyUserIdOrderByScheduledAtDescIdDesc(user.getId());
    }

    public List<Interview> findUpcoming(User user, LocalDateTime from) {
        return interviewRepository
                .findByApplicationCompanyUserIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAscIdAsc(user.getId(), from);
    }

    public Interview findOwned(User user, Long id) {
        return interviewRepository.findByIdAndApplicationCompanyUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // 同じ応募の同じ回が既にあるか（他人の応募IDなら先に404にして、存在を漏らさない）
    public boolean isRoundTaken(User user, Long applicationId, int round) {
        applicationService.findOwned(user, applicationId);
        return interviewRepository.existsByApplicationIdAndRound(applicationId, round);
    }

    public boolean isRoundTakenByOther(User user, Long applicationId, int round, Long id) {
        applicationService.findOwned(user, applicationId);
        return interviewRepository.existsByApplicationIdAndRoundAndIdNot(applicationId, round, id);
    }

    public void create(User user, InterviewForm form) {
        Interview interview = new Interview();
        applyForm(user, interview, form);
        interviewRepository.save(interview);
    }

    public void update(User user, Long id, InterviewForm form) {
        Interview interview = findOwned(user, id);
        applyForm(user, interview, form);
        interviewRepository.save(interview);
    }

    public void delete(User user, Long id) {
        Interview interview = findOwned(user, id);
        interviewRepository.delete(interview);
    }

    // フォームの値をエンティティにコピー（応募IDは所有者を確認してから使う）
    private void applyForm(User user, Interview interview, InterviewForm form) {
        interview.setApplication(applicationService.findOwned(user, form.getApplicationId()));
        interview.setRound(form.getRound());
        interview.setScheduledAt(form.getScheduledAt());
        interview.setFormat(form.getFormat());
        interview.setResult(form.getResult());
        interview.setNotes(form.getNotes());
    }
}
