package com.zorth.ssh.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import com.zorth.ssh.entity.SSHProfile;
import com.zorth.ssh.service.SSHProfileService;
import com.zorth.ssh.service.SSHService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TerminalWebSocketHandler {

    private final SSHService sshService;
    private final SSHProfileService sshProfileService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    @MessageMapping("/connect")
    public void handleConnect(@Payload String payload, SimpMessageHeaderAccessor headerAccessor) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String profileIdStr = json.get("profileId").asText();
            String sessionId = json.get("sessionId").asText();
            
            log.info("WebSocket connect request for profile: {} from session: {}", profileIdStr, sessionId);
            
            // Check if session already has an active connection
            if (sessions.containsKey(sessionId) || channels.containsKey(sessionId)) {
                log.warn("Session {} already has an active SSH connection, ignoring duplicate request", sessionId);
                messagingTemplate.convertAndSend(
                    "/topic/terminal-" + sessionId,
                    Map.of("type", "ERROR", "message", "Connection already exists for this session")
                );
                return;
            }
            
            Long profileId = Long.parseLong(profileIdStr);
            SSHProfile profile = sshProfileService.findById(profileId);
            log.info("Found SSH profile: {}", profile.getNickname());
            
            Session session = sshService.createSession(profile);
            sshService.connectSession(session);
            log.info("SSH session connected for session: {}", sessionId);

            ChannelShell channel = sshService.createShellChannel(session);
            sshService.connectChannel(channel);
            log.info("SSH channel connected for session: {}", sessionId);

            sessions.put(sessionId, session);
            channels.put(sessionId, channel);

            // Start reading from the channel
            startReadingFromChannel(channel, sessionId);

            log.info("Sending connection established message to session: {}", sessionId);
            messagingTemplate.convertAndSend(
                "/topic/terminal-" + sessionId,
                Map.of("type", "CONNECTED", "message", "Connection established to " + profile.getNickname())
            );
            log.info("Connection established message sent for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Error establishing SSH connection: ", e);
            String sessionId = headerAccessor.getSessionId();
            cleanupSession(sessionId);
            messagingTemplate.convertAndSend(
                "/topic/terminal-" + sessionId,
                Map.of("type", "ERROR", "message", "Failed to connect: " + e.getMessage())
            );
        }
    }

    @MessageMapping("/input")
    public void handleInput(@Payload String payload, SimpMessageHeaderAccessor headerAccessor) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String input = json.get("input").asText();
            String sessionId = json.get("sessionId").asText();
            
            log.debug("Received input from session {}: {}", sessionId, input.replace("\r", "\\r").replace("\n", "\\n"));
            
            Channel channel = channels.get(sessionId);
            if (channel != null && channel.isConnected()) {
                OutputStream out = channel.getOutputStream();
                out.write(input.getBytes("UTF-8"));
                out.flush();
            } else {
                log.warn("No active channel found for session: {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Error handling input: ", e);
        }
    }

    @MessageMapping("/resize")
    public void handleResize(@Payload String payload, SimpMessageHeaderAccessor headerAccessor) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String sessionId = json.get("sessionId").asText();
            int cols = json.get("cols").asInt();
            int rows = json.get("rows").asInt();
            
            log.debug("Received resize request from session {}: {}x{}", sessionId, cols, rows);
            
            Channel channel = channels.get(sessionId);
            if (channel != null && channel.isConnected()) {
                sshService.resizeChannel(channel, cols, rows);
                log.debug("Terminal resized for session {}: {}x{}", sessionId, cols, rows);
            } else {
                log.warn("No active channel found for session: {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Error handling resize: ", e);
        }
    }

    @MessageMapping("/disconnect")
    public void handleDisconnect(@Payload String payload, SimpMessageHeaderAccessor headerAccessor) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String sessionId = json.get("sessionId").asText();
            
            log.info("Disconnect request from session: {}", sessionId);
            
            cleanupSession(sessionId);

            messagingTemplate.convertAndSend(
                "/topic/terminal-" + sessionId,
                Map.of("type", "DISCONNECTED", "message", "Session ended")
            );
        } catch (Exception e) {
            log.error("Error handling disconnect: ", e);
        }
    }

    @MessageMapping("/test")
    public void handleTest(@Payload String payload, SimpMessageHeaderAccessor headerAccessor) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String sessionId = json.get("sessionId").asText();
            
            log.info("Test message from session: {}", sessionId);
            log.info("All session IDs in memory: {}", sessions.keySet());
            log.info("All channel IDs in memory: {}", channels.keySet());
            
            messagingTemplate.convertAndSend(
                "/topic/terminal-" + sessionId,
                Map.of("type", "TEST", "message", "Test message received for session: " + sessionId)
            );
            log.info("Test message sent to session: {}", sessionId);
        } catch (Exception e) {
            log.error("Error handling test message: ", e);
        }
    }

    private void startReadingFromChannel(Channel channel, String sessionId) {
        new Thread(() -> {
            log.info("Starting to read from SSH channel for session: {}", sessionId);
            try {
                InputStream in = channel.getInputStream();
                byte[] buffer = new byte[4096];
                int i;
                while ((i = in.read(buffer)) != -1) {
                    String output = new String(buffer, 0, i, "UTF-8");
                    messagingTemplate.convertAndSend(
                        "/topic/terminal-" + sessionId,
                        Map.of("type", "OUTPUT", "data", output)
                    );
                }
            } catch (IOException e) {
                log.error("Error reading from SSH channel for session {}: ", sessionId, e);
                messagingTemplate.convertAndSend(
                    "/topic/terminal-" + sessionId,
                    Map.of("type", "ERROR", "message", "Connection lost: " + e.getMessage())
                );
            } finally {
                log.info("SSH channel reading thread ended for session: {}", sessionId);
                cleanupSession(sessionId);
            }
        }).start();
    }

    private void cleanupSession(String sessionId) {
        Channel channel = channels.remove(sessionId);
        Session session = sessions.remove(sessionId);

        if (channel != null) {
            sshService.disconnectChannel(channel);
            log.info("SSH channel disconnected for session: {}", sessionId);
        }
        if (session != null) {
            sshService.disconnectSession(session);
            log.info("SSH session disconnected for session: {}", sessionId);
        }
    }
} 