package com.zorth.ssh.service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ProgressTrackingOutputStream extends FilterOutputStream {
    
    private final TransferProgressTracker progressTracker;
    private final String transferId;
    private long totalBytesWritten = 0;
    
    public ProgressTrackingOutputStream(OutputStream out, TransferProgressTracker progressTracker, String transferId) {
        super(out);
        this.progressTracker = progressTracker;
        this.transferId = transferId;
    }
    
    @Override
    public void write(int b) throws IOException {
        super.write(b);
        totalBytesWritten++;
        progressTracker.updateProgress(transferId, totalBytesWritten);
    }
    
    @Override
    public void write(byte[] b) throws IOException {
        super.write(b);
        totalBytesWritten += b.length;
        progressTracker.updateProgress(transferId, totalBytesWritten);
    }
    
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);
        totalBytesWritten += len;
        progressTracker.updateProgress(transferId, totalBytesWritten);
    }
} 