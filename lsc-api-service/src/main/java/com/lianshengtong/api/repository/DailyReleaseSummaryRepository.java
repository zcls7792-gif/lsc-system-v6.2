package com.lianshengtong.api.repository;

import com.lianshengtong.api.entity.DailyReleaseSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyReleaseSummaryRepository extends JpaRepository<DailyReleaseSummary, Long> {
}
