package com.zorth.ssh.service;

import com.zorth.ssh.dto.TransferProgress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class TransferProgressTracker {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final ConcurrentMap<String, TransferProgress> activeTransfers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SpeedCalculator> speedCalculators = new ConcurrentHashMap<>();
    
    public TransferProgressTracker(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    public static class SpeedCalculator {
        private long lastBytes = 0;
        private LocalDateTime lastTime = LocalDateTime.now();
        private double smoothedSpeed = 0;
        private static final double SMOOTHING_FACTOR = 0.3; // For exponential smoothing
        
        public long calculateSpeed(long currentBytes) {
            LocalDateTime now = LocalDateTime.now();
            long timeDiffMs = ChronoUnit.MILLIS.between(lastTime, now);
            
            if (timeDiffMs > 0 && currentBytes > lastBytes) {
                long bytesDiff = currentBytes - lastBytes;
                double currentSpeed = (bytesDiff * 1000.0) / timeDiffMs; // bytes per second
                
                // Apply exponential smoothing to reduce speed fluctuations
                if (smoothedSpeed == 0) {
                    smoothedSpeed = currentSpeed;
                } else {
                    smoothedSpeed = SMOOTHING_FACTOR * currentSpeed + (1 - SMOOTHING_FACTOR) * smoothedSpeed;
                }
                
                lastBytes = currentBytes;
                lastTime = now;
                
                return (long) smoothedSpeed;
            }
            
            return (long) smoothedSpeed;
        }
    }
    
    public void startTransfer(String transferId, String fileName, String operation, long totalBytes) {
        TransferProgress progress = new TransferProgress();
        progress.setSessionId(transferId);
        progress.setFileName(fileName);
        progress.setOperation(operation);
        progress.setTotalBytes(totalBytes);
        progress.setTransferredBytes(0);
        progress.setPercentage(0);
        progress.setStartTime(LocalDateTime.now());
        progress.setLastUpdate(LocalDateTime.now());
        progress.setStatus(TransferProgress.TransferStatus.STARTING);
        
        activeTransfers.put(transferId, progress);
        speedCalculators.put(transferId, new SpeedCalculator());
        
        // Send initial progress
        sendProgressUpdate(transferId);
        
        log.info("Started tracking transfer: {} - {} {}", transferId, operation, fileName);
    }
    
    public void updateProgress(String transferId, long transferredBytes) {
        TransferProgress progress = activeTransfers.get(transferId);
        if (progress == null) return;
        
        SpeedCalculator calculator = speedCalculators.get(transferId);
        if (calculator != null) {
            long speed = calculator.calculateSpeed(transferredBytes);
            progress.setSpeedBytesPerSecond(speed);
            progress.setSpeedFormatted(TransferProgress.formatSpeed(speed));
            
            // Calculate estimated remaining time
            if (speed > 0 && progress.getTotalBytes() > transferredBytes) {
                long remainingBytes = progress.getTotalBytes() - transferredBytes;
                progress.setEstimatedRemainingSeconds(remainingBytes / speed);
            }
        }
        
        progress.updateProgress(transferredBytes);
        progress.setStatus(TransferProgress.TransferStatus.IN_PROGRESS);
        
        // Send progress update every 1% or every 1MB, whichever is smaller
        boolean shouldUpdate = false;
        if (progress.getTotalBytes() > 0) {
            double percentageChange = (double) transferredBytes / progress.getTotalBytes() * 100.0;
            shouldUpdate = (percentageChange - progress.getPercentage()) >= 1.0;
        }
        
        if (!shouldUpdate && transferredBytes - progress.getTransferredBytes() >= 1024 * 1024) { // 1MB
            shouldUpdate = true;
        }
        
        if (shouldUpdate) {
            sendProgressUpdate(transferId);
        }
    }
    
    public void completeTransfer(String transferId) {
        TransferProgress progress = activeTransfers.get(transferId);
        if (progress != null) {
            progress.setStatus(TransferProgress.TransferStatus.COMPLETED);
            progress.updateProgress(progress.getTotalBytes());
            progress.setPercentage(100.0);
            sendProgressUpdate(transferId);
            
            // Clean up after a delay
            cleanupTransfer(transferId);
            log.info("Completed transfer: {}", transferId);
        }
    }
    
    public void failTransfer(String transferId, String errorMessage) {
        TransferProgress progress = activeTransfers.get(transferId);
        if (progress != null) {
            progress.setStatus(TransferProgress.TransferStatus.FAILED);
            progress.setErrorMessage(errorMessage);
            sendProgressUpdate(transferId);
            
            cleanupTransfer(transferId);
            log.error("Failed transfer: {} - {}", transferId, errorMessage);
        }
    }
    
    public void cancelTransfer(String transferId) {
        TransferProgress progress = activeTransfers.get(transferId);
        if (progress != null) {
            progress.setStatus(TransferProgress.TransferStatus.CANCELLED);
            sendProgressUpdate(transferId);
            
            cleanupTransfer(transferId);
            log.info("Cancelled transfer: {}", transferId);
        }
    }
    
    public TransferProgress getProgress(String transferId) {
        return activeTransfers.get(transferId);
    }
    
    private void sendProgressUpdate(String transferId) {
        TransferProgress progress = activeTransfers.get(transferId);
        if (progress != null) {
            // Send to WebSocket topic for real-time updates
            messagingTemplate.convertAndSend("/topic/transfer-progress/" + transferId, progress);
        }
    }
    
    private void cleanupTransfer(String transferId) {
        // Clean up after 5 seconds to allow frontend to get final status
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                activeTransfers.remove(transferId);
                speedCalculators.remove(transferId);
                log.debug("Cleaned up transfer tracking: {}", transferId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
} 