package com.zorth.ssh.service;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.zorth.ssh.dto.SFTPFileInfo;
import com.zorth.ssh.entity.SSHProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SFTPService {
    
    private final SSHProfileService sshProfileService;
    private final SFTPSessionManager sessionManager;
    private final TransferProgressTracker progressTracker;
    
    /**
     * Establishes an SFTP connection using stored credentials
     */
    public String connect(Long profileId) throws JSchException {
        SSHProfile profile = sshProfileService.findById(profileId);
        return sessionManager.createSession(profile);
    }
    
    /**
     * Lists files and directories in a given path on the target server
     */
    public List<SFTPFileInfo> listFiles(String sessionId, String remotePath) throws SftpException {
        ChannelSftp sftpChannel = sessionManager.getChannel(sessionId);
        
        log.info("Listing files in path: {}", remotePath);
        
        Vector<ChannelSftp.LsEntry> fileList = sftpChannel.ls(remotePath);
        
        return fileList.stream()
                .filter(entry -> !".".equals(entry.getFilename()) && !"..".equals(entry.getFilename()))
                .map(entry -> convertToFileInfo(entry, remotePath))
                .collect(Collectors.toList());
    }
    
    /**
     * Downloads a file from the target server via SFTP and writes it to an output stream
     * Uses the provided transferId for progress tracking
     */
    public void downloadFileWithProgress(String sessionId, String remotePath, OutputStream outputStream, String transferId) 
            throws SftpException, IOException {
        ChannelSftp sftpChannel = sessionManager.getChannel(sessionId);
        String fileName = getFileName(remotePath);
        
        try {
            // Get file size for progress tracking using stat instead of ls
            long fileSize;
            try {
                fileSize = sftpChannel.stat(remotePath).getSize();
            } catch (SftpException e) {
                log.warn("Could not get file size for {}, proceeding without progress tracking", remotePath);
                fileSize = -1; // Unknown size
            }
            
            // Only start progress tracking if not already started
            if (progressTracker.getProgress(transferId) == null) {
                progressTracker.startTransfer(transferId, fileName, "DOWNLOAD", fileSize);
            }
            
            log.info("Downloading file: {} (size: {} bytes)", remotePath, fileSize);
            
            // Wrap output stream with progress tracking
            ProgressTrackingOutputStream progressOutputStream = 
                    new ProgressTrackingOutputStream(outputStream, progressTracker, transferId);
            
            try (InputStream inputStream = sftpChannel.get(remotePath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    progressOutputStream.write(buffer, 0, bytesRead);
                }
                progressOutputStream.flush();
            }
            
            progressTracker.completeTransfer(transferId);
            log.info("Successfully downloaded file: {}", remotePath);
            
        } catch (Exception e) {
            progressTracker.failTransfer(transferId, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Legacy method that generates its own transferId
     */
    public String downloadFileWithProgress(String sessionId, String remotePath, OutputStream outputStream) 
            throws SftpException, IOException {
        String transferId = UUID.randomUUID().toString();
        downloadFileWithProgress(sessionId, remotePath, outputStream, transferId);
        return transferId;
    }
    
    /**
     * Uploads a file to the target server via SFTP with progress tracking
     * Uses the provided transferId for progress tracking
     */
    public void uploadFileWithProgress(String sessionId, String remotePath, InputStream inputStream, long fileSize, String transferId)
            throws SftpException {
        ChannelSftp sftpChannel = sessionManager.getChannel(sessionId);
        String fileName = getFileName(remotePath);
        
        log.info("uploadFileWithProgress called with transferId: {} (is null: {})", transferId, transferId == null);
        
        // Generate transferId if not provided (for legacy calls)
        if (transferId == null) {
            transferId = UUID.randomUUID().toString();
            log.info("Generated new transferId for legacy call: {}", transferId);
            // Only start progress tracking for legacy calls where controller didn't start it
            progressTracker.startTransfer(transferId, fileName, "UPLOAD", fileSize);
        }
        
        try {
            log.info("Uploading file to: {} (size: {} bytes) with transferId: {}", remotePath, fileSize, transferId);

            // Wrap input stream with progress tracking
            ProgressTrackingInputStream progressInputStream =
                    new ProgressTrackingInputStream(inputStream, progressTracker, transferId);

            sftpChannel.put(progressInputStream, remotePath);

            progressTracker.completeTransfer(transferId);
            log.info("Successfully uploaded file to: {} with transferId: {}", remotePath, transferId);

        } catch (Exception e) {
            progressTracker.failTransfer(transferId, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    public void uploadFile(String sessionId, String remotePath, InputStream inputStream) 
            throws SftpException {
        // For legacy calls, generate a transferId and proceed normally
        String transferId = UUID.randomUUID().toString();
        uploadFileWithProgress(sessionId, remotePath, inputStream, -1, transferId);
    }
    
    /**
     * Creates a directory on the target server
     */
    public void createDirectory(String sessionId, String remotePath) throws SftpException {
        ChannelSftp sftpChannel = sessionManager.getChannel(sessionId);
        
        log.info("Creating directory: {}", remotePath);
        
        sftpChannel.mkdir(remotePath);
        
        log.info("Successfully created directory: {}", remotePath);
    }
    
    /**
     * Deletes a file or directory on the target server
     */
    public void deleteFile(String sessionId, String remotePath, boolean isDirectory) throws SftpException {
        ChannelSftp sftpChannel = sessionManager.getChannel(sessionId);
        
        log.info("Deleting {}: {}", isDirectory ? "directory" : "file", remotePath);
        
        if (isDirectory) {
            sftpChannel.rmdir(remotePath);
        } else {
            sftpChannel.rm(remotePath);
        }
        
        log.info("Successfully deleted {}: {}", isDirectory ? "directory" : "file", remotePath);
    }
    
    /**
     * Renames/moves a file or directory on the target server
     */
    public void renameFile(String sessionId, String oldPath, String newPath) throws SftpException {
        ChannelSftp sftpChannel = sessionManager.getChannel(sessionId);
        
        log.info("Renaming from {} to {}", oldPath, newPath);
        
        sftpChannel.rename(oldPath, newPath);
        
        log.info("Successfully renamed from {} to {}", oldPath, newPath);
    }
    
    /**
     * Gets file information for a specific file
     */
    public SFTPFileInfo getFileInfo(String sessionId, String remotePath) throws SftpException {
        ChannelSftp sftpChannel = sessionManager.getChannel(sessionId);
        
        Vector<ChannelSftp.LsEntry> fileList = sftpChannel.ls(remotePath);
        ChannelSftp.LsEntry entry = fileList.stream()
                .filter(e -> e.getFilename().equals(getFileName(remotePath)))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + remotePath));
        
        return convertToFileInfo(entry, getParentPath(remotePath));
    }
    
    /**
     * Closes the SFTP channel/session
     */
    public void disconnect(String sessionId) {
        sessionManager.closeSession(sessionId);
        log.info("Disconnected SFTP session: {}", sessionId);
    }
    
    private SFTPFileInfo convertToFileInfo(ChannelSftp.LsEntry entry, String parentPath) {
        SFTPFileInfo fileInfo = new SFTPFileInfo();
        fileInfo.setName(entry.getFilename());
        fileInfo.setPath(parentPath.endsWith("/") ? parentPath + entry.getFilename() : parentPath + "/" + entry.getFilename());
        
        boolean isDir = entry.getAttrs().isDir();
        fileInfo.setDirectory(isDir);
        log.debug("File: {} - isDirectory: {}", entry.getFilename(), isDir);
        
        fileInfo.setSize(entry.getAttrs().getSize());
        
        // Convert modification time
        long mTime = entry.getAttrs().getMTime();
        if (mTime > 0) {
            fileInfo.setLastModified(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(mTime), ZoneId.systemDefault()));
        }
        
        // Set permissions
        int permissions = entry.getAttrs().getPermissions();
        fileInfo.setPermissions(convertPermissions(permissions));
        
        // Set owner and group IDs (JSch doesn't provide names, only IDs)
        fileInfo.setOwner(String.valueOf(entry.getAttrs().getUId()));
        fileInfo.setGroup(String.valueOf(entry.getAttrs().getGId()));
        
        return fileInfo;
    }
    
    private String convertPermissions(int permissions) {
        StringBuilder sb = new StringBuilder();
        
        // File type
        if ((permissions & 0040000) != 0) sb.append("d");
        else if ((permissions & 0120000) != 0) sb.append("l");
        else sb.append("-");
        
        // Owner permissions
        sb.append((permissions & 0400) != 0 ? "r" : "-");
        sb.append((permissions & 0200) != 0 ? "w" : "-");
        sb.append((permissions & 0100) != 0 ? "x" : "-");
        
        // Group permissions
        sb.append((permissions & 0040) != 0 ? "r" : "-");
        sb.append((permissions & 0020) != 0 ? "w" : "-");
        sb.append((permissions & 0010) != 0 ? "x" : "-");
        
        // Other permissions
        sb.append((permissions & 0004) != 0 ? "r" : "-");
        sb.append((permissions & 0002) != 0 ? "w" : "-");
        sb.append((permissions & 0001) != 0 ? "x" : "-");
        
        return sb.toString();
    }
    
    private String getFileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
    
    private String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash > 0 ? path.substring(0, lastSlash) : "/";
    }
} 