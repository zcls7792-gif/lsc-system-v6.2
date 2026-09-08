package com.lianshengtong.api.repository;

import com.lianshengtong.api.entity.Writeoff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WriteoffRepository extends JpaRepository<Writeoff, Long> {
}
