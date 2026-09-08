package com.lianshengtong.api.repository;

import com.lianshengtong.api.entity.LscAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LscAccountRepository extends JpaRepository<LscAccount, Long> {
}
