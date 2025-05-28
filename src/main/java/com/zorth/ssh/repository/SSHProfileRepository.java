package com.zorth.ssh.repository;

import com.zorth.ssh.entity.SSHProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SSHProfileRepository extends JpaRepository<SSHProfile, Long> {
    // 移除了User相关的方法，因为不再使用用户认证
} 