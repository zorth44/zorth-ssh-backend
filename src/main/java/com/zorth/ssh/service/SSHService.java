package com.zorth.ssh.service;

import com.jcraft.jsch.*;
import com.zorth.ssh.entity.SSHProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Properties;

@Slf4j
@Service
public class SSHService {

    public Session createSession(SSHProfile profile) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(profile.getUsername(), profile.getHost(), profile.getPort());

        if (profile.getAuthType() == SSHProfile.AuthType.PASSWORD) {
            session.setPassword(profile.getEncryptedPassword());
        } else {
            // Handle SSH key authentication
            jsch.addIdentity("key", profile.getEncryptedPrivateKey().getBytes(), null, 
                    profile.getKeyPassphraseEncrypted() != null ? 
                    profile.getKeyPassphraseEncrypted().getBytes() : null);
        }

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        // 设置字符编码
        config.put("file.encoding", "UTF-8");
        session.setConfig(config);

        return session;
    }

    public ChannelShell createShellChannel(Session session) throws JSchException {
        ChannelShell channel = (ChannelShell) session.openChannel("shell");
        
        // 设置终端类型和环境变量
        channel.setEnv("TERM", "xterm-256color");
        channel.setEnv("LANG", "en_US.UTF-8");
        channel.setEnv("LC_ALL", "en_US.UTF-8");
        
        // 启用PTY（伪终端）
        channel.setPty(true);
        
        // 设置默认终端大小（80x24是标准默认值）
        channel.setPtySize(80, 24, 640, 480);
        
        log.debug("Created shell channel with PTY enabled and default size 80x24");
        
        return channel;
    }

    public void connectSession(Session session) throws JSchException {
        if (!session.isConnected()) {
            session.connect();
            log.debug("SSH session connected");
        }
    }

    public void connectChannel(Channel channel) throws JSchException, IOException {
        if (!channel.isConnected()) {
            channel.connect();
            log.debug("SSH channel connected");
        }
    }

    public void disconnectChannel(Channel channel) {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
            log.debug("SSH channel disconnected");
        }
    }

    public void disconnectSession(Session session) {
        if (session != null && session.isConnected()) {
            session.disconnect();
            log.debug("SSH session disconnected");
        }
    }

    public void resizeChannel(Channel channel, int cols, int rows) {
        if (channel instanceof ChannelShell && channel.isConnected()) {
            try {
                // 计算像素大小（假设字符大小为8x16像素）
                int pixelWidth = cols * 8;
                int pixelHeight = rows * 16;
                
                ((ChannelShell) channel).setPtySize(cols, rows, pixelWidth, pixelHeight);
                log.debug("Terminal resized to {}x{} ({}x{} pixels)", cols, rows, pixelWidth, pixelHeight);
            } catch (Exception e) {
                log.error("Failed to resize terminal: ", e);
            }
        } else {
            log.warn("Cannot resize channel: channel is not a ChannelShell or not connected");
        }
    }
} 