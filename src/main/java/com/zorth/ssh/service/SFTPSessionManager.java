package com.zorth.ssh.service;

import com.jcraft.jsch.*;
import com.zorth.ssh.entity.SSHProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SFTPSessionManager {
    
    private final SSHService sshService;
    private final ConcurrentMap<String, SFTPSessionInfo> activeSessions = new ConcurrentHashMap<>();
    
    public static class SFTPSessionInfo {
        public final Session session;
        public final ChannelSftp sftpChannel;
        public final long lastUsed;
        
        public SFTPSessionInfo(Session session, ChannelSftp sftpChannel) {
            this.session = session;
            this.sftpChannel = sftpChannel;
            this.lastUsed = System.currentTimeMillis();
        }
    }
    
    public String createSession(SSHProfile profile) throws JSchException {
        String sessionId = generateSessionId(profile);
        
        // Check if session already exists and is valid
        SFTPSessionInfo existingSession = activeSessions.get(sessionId);
        if (existingSession != null && isSessionValid(existingSession)) {
            log.debug("Reusing existing SFTP session for profile: {}", profile.getId());
            return sessionId;
        }
        
        // Clean up invalid session if exists
        if (existingSession != null) {
            cleanupSession(sessionId);
        }
        
        // Create new session
        Session session = sshService.createSession(profile);
        sshService.connectSession(session);
        
        ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect();
        
        activeSessions.put(sessionId, new SFTPSessionInfo(session, sftpChannel));
        log.info("Created new SFTP session for profile: {}", profile.getId());
        
        return sessionId;
    }
    
    public ChannelSftp getChannel(String sessionId) {
        SFTPSessionInfo sessionInfo = activeSessions.get(sessionId);
        if (sessionInfo == null || !isSessionValid(sessionInfo)) {
            throw new RuntimeException("SFTP session not found or invalid: " + sessionId);
        }
        return sessionInfo.sftpChannel;
    }
    
    public void closeSession(String sessionId) {
        cleanupSession(sessionId);
    }
    
    public void closeAllSessions() {
        log.info("Closing all SFTP sessions");
        activeSessions.keySet().forEach(this::cleanupSession);
    }
    
    private String generateSessionId(SSHProfile profile) {
        return String.format("sftp_%d_%s_%s_%d", 
            profile.getId(), 
            profile.getUsername(), 
            profile.getHost(), 
            profile.getPort());
    }
    
    private boolean isSessionValid(SFTPSessionInfo sessionInfo) {
        return sessionInfo.session.isConnected() && 
               sessionInfo.sftpChannel.isConnected() && 
               !sessionInfo.sftpChannel.isClosed();
    }
    
    private void cleanupSession(String sessionId) {
        SFTPSessionInfo sessionInfo = activeSessions.remove(sessionId);
        if (sessionInfo != null) {
            try {
                if (sessionInfo.sftpChannel.isConnected()) {
                    sessionInfo.sftpChannel.disconnect();
                }
                if (sessionInfo.session.isConnected()) {
                    sessionInfo.session.disconnect();
                }
                log.debug("Cleaned up SFTP session: {}", sessionId);
            } catch (Exception e) {
                log.warn("Error cleaning up SFTP session {}: {}", sessionId, e.getMessage());
            }
        }
    }
} 