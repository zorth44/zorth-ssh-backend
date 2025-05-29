package com.zorth.ssh.service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProgressTrackingOutputStream extends FilterOutputStream {
    
    private final TransferProgressTracker progressTracker;
    private final String transferId;
    private long totalBytesWritten = 0;
    private long lastUpdateBytes = 0;
    private static final long UPDATE_THRESHOLD = 256 * 1024; // 256KB
    
    public ProgressTrackingOutputStream(OutputStream out, TransferProgressTracker progressTracker, String transferId) {
        super(out);
        this.progressTracker = progressTracker;
        this.transferId = transferId;
        log.debug("Created ProgressTrackingOutputStream for transfer: {}", transferId);
    }
    
    @Override
    public void write(int b) throws IOException {
        super.write(b);
        totalBytesWritten++;
        updateProgress();
    }
    
    @Override
    public void write(byte[] b) throws IOException {
        super.write(b);
        totalBytesWritten += b.length;
        updateProgress();
    }
    
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);
        totalBytesWritten += len;
        updateProgress();
    }
    
    private void updateProgress() {
        // Only update if we've written enough new bytes
        if (totalBytesWritten - lastUpdateBytes >= UPDATE_THRESHOLD) {
            log.debug("Updating progress for transfer {}: {} bytes written", transferId, totalBytesWritten);
            progressTracker.updateProgress(transferId, totalBytesWritten);
            lastUpdateBytes = totalBytesWritten;
        }
    }
    
    @Override
    public void close() throws IOException {
        // Send final progress update
        if (totalBytesWritten > lastUpdateBytes) {
            log.debug("Sending final progress update for transfer {}: {} bytes written", transferId, totalBytesWritten);
            progressTracker.updateProgress(transferId, totalBytesWritten);
        }
        super.close();
    }
} 