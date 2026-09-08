package com.lianshengtong.api.repository;

import com.lianshengtong.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByReferrerId(Long referrerId);
    List<User> findByUserType(Integer userType);
}
