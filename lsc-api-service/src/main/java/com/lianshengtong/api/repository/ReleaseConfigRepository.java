package com.lianshengtong.api.repository;

import com.lianshengtong.api.entity.ReleaseConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReleaseConfigRepository extends JpaRepository<ReleaseConfig, Integer> {
    List<ReleaseConfig> findByConfigKey(String key);
}
