package com.lianshengtong.api.repository;

import com.lianshengtong.api.entity.RiskLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskLogRepository extends JpaRepository<RiskLog, Long> {
}
