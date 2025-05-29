package com.zorth.ssh.config;

import com.zorth.ssh.service.SFTPSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PreDestroy;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SFTPConfig {
    
    private final SFTPSessionManager sessionManager;
    
    /**
     * Clean up idle SFTP sessions every 30 minutes
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void cleanupIdleSessions() {
        log.debug("Running scheduled SFTP session cleanup");
        // This could be enhanced to clean up sessions idle for more than X minutes
        // For now, we'll let the session manager handle connection validation
    }
    
    /**
     * Clean up all sessions on application shutdown
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SFTP service - cleaning up all sessions");
        sessionManager.closeAllSessions();
    }
} 