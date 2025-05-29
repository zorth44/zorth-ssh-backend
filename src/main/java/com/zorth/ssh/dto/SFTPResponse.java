package com.zorth.ssh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SFTPResponse<T> {
    private boolean success;
    private String message;
    private T data;
    
    public static <T> SFTPResponse<T> success(T data) {
        return new SFTPResponse<>(true, "Operation completed successfully", data);
    }
    
    public static <T> SFTPResponse<T> success(String message, T data) {
        return new SFTPResponse<>(true, message, data);
    }
    
    public static <T> SFTPResponse<T> error(String message) {
        return new SFTPResponse<>(false, message, null);
    }
} 