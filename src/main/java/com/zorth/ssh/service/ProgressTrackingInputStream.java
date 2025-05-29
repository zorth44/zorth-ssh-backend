package com.zorth.ssh.service;

import lombok.extern.slf4j.Slf4j;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class ProgressTrackingInputStream extends FilterInputStream {
    
    private final TransferProgressTracker progressTracker;
    private final String transferId;
    private long totalBytesRead = 0;
    private long lastUpdateBytes = 0;
    private static final long UPDATE_THRESHOLD = 64 * 1024; // 64KB
    private static final long MIN_UPDATE_INTERVAL_MS = 100; // Minimum time between updates
    private long lastUpdateTime = System.currentTimeMillis();
    
    public ProgressTrackingInputStream(InputStream in, TransferProgressTracker progressTracker, String transferId) {
        super(in);
        this.progressTracker = progressTracker;
        this.transferId = transferId;
        log.debug("Created ProgressTrackingInputStream for transfer: {}", transferId);
    }
    
    @Override
    public int read() throws IOException {
        int result = super.read();
        if (result != -1) {
            totalBytesRead++;
            updateProgress();
        }
        return result;
    }
    
    @Override
    public int read(byte[] b) throws IOException {
        int result = super.read(b);
        if (result != -1) {
            totalBytesRead += result;
            updateProgress();
        }
        return result;
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = super.read(b, off, len);
        if (result != -1) {
            totalBytesRead += result;
            updateProgress();
        }
        return result;
    }
    
    @Override
    public long skip(long n) throws IOException {
        long result = super.skip(n);
        if (result > 0) {
            totalBytesRead += result;
            updateProgress();
        }
        return result;
    }
    
    private void updateProgress() {
        long currentTime = System.currentTimeMillis();
        // Update if we've read enough new bytes OR enough time has passed
        if ((totalBytesRead - lastUpdateBytes >= UPDATE_THRESHOLD) || 
            (currentTime - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS)) {
            log.debug("Updating progress for transfer {}: {} bytes read", transferId, totalBytesRead);
            progressTracker.updateProgress(transferId, totalBytesRead);
            lastUpdateBytes = totalBytesRead;
            lastUpdateTime = currentTime;
        }
    }
    
    @Override
    public void close() throws IOException {
        // Send final progress update
        if (totalBytesRead > lastUpdateBytes) {
            log.debug("Sending final progress update for transfer {}: {} bytes read", transferId, totalBytesRead);
            progressTracker.updateProgress(transferId, totalBytesRead);
        }
        super.close();
    }
} 