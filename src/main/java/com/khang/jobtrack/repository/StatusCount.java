package com.khang.jobtrack.repository;

import com.khang.jobtrack.entity.ApplicationStatus;

public interface StatusCount {
    ApplicationStatus getStatus();
    long getCount();
}