package com.zorth.ssh.controller;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.zorth.ssh.dto.SFTPFileInfo;
import com.zorth.ssh.dto.SFTPResponse;
import com.zorth.ssh.dto.TransferProgress;
import com.zorth.ssh.service.SFTPService;
import com.zorth.ssh.service.TransferProgressTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/sftp")
@RequiredArgsConstructor
public class SFTPController {

    private final SFTPService sftpService;
    private final TransferProgressTracker progressTracker;

    /**
     * Establish SFTP connection
     */
    @PostMapping("/{profileId}/connect")
    public ResponseEntity<SFTPResponse<String>> connect(@PathVariable Long profileId) {
        try {
            String sessionId = sftpService.connect(profileId);
            return ResponseEntity.ok(SFTPResponse.success("Connected successfully", sessionId));
        } catch (JSchException e) {
            log.error("Failed to connect to SFTP for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to connect: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error connecting to SFTP for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(SFTPResponse.error("Internal server error"));
        }
    }

    /**
     * List files and directories in a remote path
     */
    @GetMapping("/{profileId}/list")
    public ResponseEntity<SFTPResponse<List<SFTPFileInfo>>> listFiles(
            @PathVariable Long profileId,
            @RequestParam(defaultValue = "/") String path) {
        try {
            String sessionId = sftpService.connect(profileId);
            List<SFTPFileInfo> files = sftpService.listFiles(sessionId, path);
            return ResponseEntity.ok(SFTPResponse.success(files));
        } catch (SftpException e) {
            log.error("Failed to list files in path {} for profile {}: {}", path, profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to list files: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error listing files for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(SFTPResponse.error("Internal server error"));
        }
    }

    /**
     * Download a file from the remote server with progress tracking
     */
    @GetMapping("/{profileId}/download")
    public ResponseEntity<?> downloadFile(
            @PathVariable Long profileId,
            @RequestParam String path) {
        try {
            String sessionId = sftpService.connect(profileId);
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            
            StreamingResponseBody streamingResponseBody = outputStream -> {
                try {
                    String transferId = sftpService.downloadFileWithProgress(sessionId, path, outputStream);
                    log.info("Download started with transfer ID: {}", transferId);
                } catch (Exception e) {
                    log.error("Error during file download: {}", e.getMessage());
                    throw new RuntimeException("Download failed", e);
                }
            };

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(streamingResponseBody);
                    
        } catch (Exception e) {
            log.error("Failed to download file {} for profile {}: {}", path, profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to download file: " + e.getMessage()));
        }
    }

    /**
     * Upload a file to the remote server with progress tracking
     */
    @PostMapping("/{profileId}/upload")
    public ResponseEntity<SFTPResponse<Map<String, String>>> uploadFile(
            @PathVariable Long profileId,
            @RequestParam String path,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(SFTPResponse.error("File is empty"));
            }

            String sessionId = sftpService.connect(profileId);
            String remotePath = path.endsWith("/") ? path + file.getOriginalFilename() : path + "/" + file.getOriginalFilename();
            
            // Start upload with progress tracking
            String transferId = sftpService.uploadFileWithProgress(sessionId, remotePath, file.getInputStream(), file.getSize());
            
            Map<String, String> result = Map.of(
                "transferId", transferId,
                "remotePath", remotePath,
                "fileName", file.getOriginalFilename()
            );
            
            return ResponseEntity.ok(SFTPResponse.success("File upload started", result));
        } catch (SftpException e) {
            log.error("Failed to upload file to {} for profile {}: {}", path, profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to upload file: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error uploading file for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(SFTPResponse.error("Internal server error"));
        }
    }

    /**
     * Get transfer progress for a specific transfer ID
     */
    @GetMapping("/progress/{transferId}")
    public ResponseEntity<SFTPResponse<TransferProgress>> getTransferProgress(@PathVariable String transferId) {
        try {
            TransferProgress progress = progressTracker.getProgress(transferId);
            if (progress == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(SFTPResponse.success(progress));
        } catch (Exception e) {
            log.error("Error getting transfer progress for {}: {}", transferId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(SFTPResponse.error("Internal server error"));
        }
    }

    /**
     * Cancel a transfer
     */
    @PostMapping("/progress/{transferId}/cancel")
    public ResponseEntity<SFTPResponse<String>> cancelTransfer(@PathVariable String transferId) {
        try {
            progressTracker.cancelTransfer(transferId);
            return ResponseEntity.ok(SFTPResponse.success("Transfer cancelled"));
        } catch (Exception e) {
            log.error("Error cancelling transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to cancel transfer: " + e.getMessage()));
        }
    }

    /**
     * Create a directory on the remote server
     */
    @PostMapping("/{profileId}/mkdir")
    public ResponseEntity<SFTPResponse<String>> createDirectory(
            @PathVariable Long profileId,
            @RequestParam String path) {
        try {
            String sessionId = sftpService.connect(profileId);
            sftpService.createDirectory(sessionId, path);
            return ResponseEntity.ok(SFTPResponse.success("Directory created successfully", path));
        } catch (SftpException e) {
            log.error("Failed to create directory {} for profile {}: {}", path, profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to create directory: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating directory for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(SFTPResponse.error("Internal server error"));
        }
    }

    /**
     * Delete a file or directory on the remote server
     */
    @DeleteMapping("/{profileId}/delete")
    public ResponseEntity<SFTPResponse<String>> deleteFile(
            @PathVariable Long profileId,
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean isDirectory) {
        try {
            String sessionId = sftpService.connect(profileId);
            sftpService.deleteFile(sessionId, path, isDirectory);
            return ResponseEntity.ok(SFTPResponse.success(
                    (isDirectory ? "Directory" : "File") + " deleted successfully", path));
        } catch (SftpException e) {
            log.error("Failed to delete {} {} for profile {}: {}", 
                    isDirectory ? "directory" : "file", path, profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to delete: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error deleting file for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(SFTPResponse.error("Internal server error"));
        }
    }

    /**
     * Rename/move a file or directory on the remote server
     */
    @PutMapping("/{profileId}/rename")
    public ResponseEntity<SFTPResponse<String>> renameFile(
            @PathVariable Long profileId,
            @RequestBody Map<String, String> request) {
        try {
            String oldPath = request.get("oldPath");
            String newPath = request.get("newPath");
            
            if (oldPath == null || newPath == null) {
                return ResponseEntity.badRequest()
                        .body(SFTPResponse.error("Both oldPath and newPath are required"));
            }

            String sessionId = sftpService.connect(profileId);
            sftpService.renameFile(sessionId, oldPath, newPath);
            return ResponseEntity.ok(SFTPResponse.success("File renamed successfully", newPath));
        } catch (SftpException e) {
            log.error("Failed to rename file for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to rename file: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error renaming file for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(SFTPResponse.error("Internal server error"));
        }
    }

    /**
     * Get information about a specific file
     */
    @GetMapping("/{profileId}/info")
    public ResponseEntity<SFTPResponse<SFTPFileInfo>> getFileInfo(
            @PathVariable Long profileId,
            @RequestParam String path) {
        try {
            String sessionId = sftpService.connect(profileId);
            SFTPFileInfo fileInfo = sftpService.getFileInfo(sessionId, path);
            return ResponseEntity.ok(SFTPResponse.success(fileInfo));
        } catch (SftpException e) {
            log.error("Failed to get file info for {} in profile {}: {}", path, profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to get file info: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting file info for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(SFTPResponse.error("Internal server error"));
        }
    }

    /**
     * Disconnect SFTP session
     */
    @PostMapping("/{profileId}/disconnect")
    public ResponseEntity<SFTPResponse<String>> disconnect(
            @PathVariable Long profileId,
            @RequestParam String sessionId) {
        try {
            sftpService.disconnect(sessionId);
            return ResponseEntity.ok(SFTPResponse.success("Disconnected successfully"));
        } catch (Exception e) {
            log.error("Error disconnecting SFTP session for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to disconnect: " + e.getMessage()));
        }
    }

    /**
     * Connect to SFTP and get initial directory listing (combined operation for frontend)
     */
    @PostMapping("/{profileId}/connect-and-browse")
    public ResponseEntity<SFTPResponse<Map<String, Object>>> connectAndBrowse(
            @PathVariable Long profileId,
            @RequestParam(defaultValue = "/") String initialPath) {
        try {
            String sessionId = sftpService.connect(profileId);
            List<SFTPFileInfo> files = sftpService.listFiles(sessionId, initialPath);
            
            Map<String, Object> result = Map.of(
                "sessionId", sessionId,
                "currentPath", initialPath,
                "files", files,
                "connected", true
            );
            
            return ResponseEntity.ok(SFTPResponse.success("Connected and browsing " + initialPath, result));
        } catch (JSchException e) {
            log.error("Failed to connect to SFTP for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SFTPResponse.error("Failed to connect: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error connecting to SFTP for profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(SFTPResponse.error("Internal server error"));
        }
    }

    /**
     * Get SFTP connection status for a profile
     */
    @GetMapping("/{profileId}/status")
    public ResponseEntity<SFTPResponse<Map<String, Object>>> getConnectionStatus(@PathVariable Long profileId) {
        try {
            // Try to get or create session to check connectivity
            String sessionId = sftpService.connect(profileId);
            
            Map<String, Object> status = Map.of(
                "sessionId", sessionId,
                "connected", true,
                "profileId", profileId
            );
            
            return ResponseEntity.ok(SFTPResponse.success("Connection active", status));
        } catch (Exception e) {
            Map<String, Object> status = Map.of(
                "connected", false,
                "profileId", profileId,
                "error", e.getMessage()
            );
            return ResponseEntity.ok(SFTPResponse.success("Not connected", status));
        }
    }
} 