package com.zorth.ssh.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SFTPFileInfo {
    private String name;
    private String path;
    @JsonProperty("isDirectory")
    private boolean isDirectory;
    private long size;
    private LocalDateTime lastModified;
    private String permissions;
    private String owner;
    private String group;
} 