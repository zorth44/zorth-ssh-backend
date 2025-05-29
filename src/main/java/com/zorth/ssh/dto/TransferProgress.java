package com.zorth.ssh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferProgress {
    private String sessionId;
    private String fileName;
    private String operation; // "UPLOAD" or "DOWNLOAD"
    private long totalBytes;
    private long transferredBytes;
    private double percentage;
    private long speedBytesPerSecond;
    private String speedFormatted; // e.g., "1.2 MB/s"
    private LocalDateTime startTime;
    private LocalDateTime lastUpdate;
    private long estimatedRemainingSeconds;
    private TransferStatus status;
    private String errorMessage;
    
    public enum TransferStatus {
        STARTING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    // Helper method to calculate percentage
    public void updateProgress(long transferred) {
        this.transferredBytes = transferred;
        if (totalBytes > 0) {
            this.percentage = (double) transferred / totalBytes * 100.0;
        }
        this.lastUpdate = LocalDateTime.now();
    }
    
    // Helper method to format speed
    public static String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B/s";
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
        } else if (bytesPerSecond < 1024 * 1024 * 1024) {
            return String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB/s", bytesPerSecond / (1024.0 * 1024.0 * 1024.0));
        }
    }
} 