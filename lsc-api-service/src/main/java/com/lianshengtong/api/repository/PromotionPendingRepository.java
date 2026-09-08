package com.lianshengtong.api.repository;

import com.lianshengtong.api.entity.PromotionPending;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionPendingRepository extends JpaRepository<PromotionPending, Long> {
    List<PromotionPending> findByReferrerId(Long referrerId);
    List<PromotionPending> findByStatus(Integer status);
}
