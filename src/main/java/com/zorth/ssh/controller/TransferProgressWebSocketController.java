package com.zorth.ssh.controller;

import com.zorth.ssh.dto.TransferProgress;
import com.zorth.ssh.service.TransferProgressTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TransferProgressWebSocketController {
    
    private final TransferProgressTracker progressTracker;
    
    /**
     * Handle subscription to transfer progress updates
     * Client subscribes to: /topic/transfer-progress/{transferId}
     */
    @SubscribeMapping("/transfer-progress/{transferId}")
    public TransferProgress subscribeToTransferProgress(@DestinationVariable String transferId) {
        log.debug("Client subscribed to transfer progress: {}", transferId);
        
        // Return current progress if available
        TransferProgress currentProgress = progressTracker.getProgress(transferId);
        if (currentProgress != null) {
            return currentProgress;
        }
        
        // Return empty progress object if transfer not found
        TransferProgress notFound = new TransferProgress();
        notFound.setSessionId(transferId);
        notFound.setStatus(TransferProgress.TransferStatus.STARTING);
        notFound.setPercentage(0.0);
        return notFound;
    }
    
    /**
     * Handle client requests for current transfer status
     * Client sends to: /app/transfer-status/{transferId}
     */
    @MessageMapping("/transfer-status/{transferId}")
    @SendTo("/topic/transfer-progress/{transferId}")
    public TransferProgress getTransferStatus(@DestinationVariable String transferId) {
        log.debug("Client requested transfer status: {}", transferId);
        
        TransferProgress progress = progressTracker.getProgress(transferId);
        if (progress != null) {
            return progress;
        }
        
        // Return not found status
        TransferProgress notFound = new TransferProgress();
        notFound.setSessionId(transferId);
        notFound.setStatus(TransferProgress.TransferStatus.FAILED);
        notFound.setErrorMessage("Transfer not found");
        return notFound;
    }
} 