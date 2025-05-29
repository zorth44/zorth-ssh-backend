package com.zorth.ssh.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProgressTrackingInputStream extends FilterInputStream {
    
    private final TransferProgressTracker progressTracker;
    private final String transferId;
    private long totalBytesRead = 0;
    
    public ProgressTrackingInputStream(InputStream in, TransferProgressTracker progressTracker, String transferId) {
        super(in);
        this.progressTracker = progressTracker;
        this.transferId = transferId;
    }
    
    @Override
    public int read() throws IOException {
        int result = super.read();
        if (result != -1) {
            totalBytesRead++;
            progressTracker.updateProgress(transferId, totalBytesRead);
        }
        return result;
    }
    
    @Override
    public int read(byte[] b) throws IOException {
        int result = super.read(b);
        if (result != -1) {
            totalBytesRead += result;
            progressTracker.updateProgress(transferId, totalBytesRead);
        }
        return result;
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = super.read(b, off, len);
        if (result != -1) {
            totalBytesRead += result;
            progressTracker.updateProgress(transferId, totalBytesRead);
        }
        return result;
    }
    
    @Override
    public long skip(long n) throws IOException {
        long result = super.skip(n);
        totalBytesRead += result;
        progressTracker.updateProgress(transferId, totalBytesRead);
        return result;
    }
} 