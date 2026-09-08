package com.lianshengtong.api.repository;

import com.lianshengtong.api.entity.LedgerTxn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerTxnRepository extends JpaRepository<LedgerTxn, Long> {
}
