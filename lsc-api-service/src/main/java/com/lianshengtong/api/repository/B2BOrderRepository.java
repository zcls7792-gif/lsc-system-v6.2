package com.lianshengtong.api.repository;

import com.lianshengtong.api.entity.B2BOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface B2BOrderRepository extends JpaRepository<B2BOrder, Long> {
}
