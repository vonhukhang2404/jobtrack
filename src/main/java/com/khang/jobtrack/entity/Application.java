package com.khang.jobtrack.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 100)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.NOT_APPLIED;

    @Column(name = "applied_at")
    private LocalDate appliedAt;

    @Column(name = "jd_text", columnDefinition = "TEXT")
    private String jdText;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Application() {
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public String getPosition() {
        return position;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public LocalDate getAppliedAt() {
        return appliedAt;
    }

    public String getJdText() {
        return jdText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public void setAppliedAt(LocalDate appliedAt) {
        this.appliedAt = appliedAt;
    }

    public void setJdText(String jdText) {
        this.jdText = jdText;
    }
}