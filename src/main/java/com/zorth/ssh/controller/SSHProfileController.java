package com.zorth.ssh.controller;

import com.zorth.ssh.entity.SSHProfile;
import com.zorth.ssh.service.SSHProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
public class SSHProfileController {

    private final SSHProfileService sshProfileService;

    @GetMapping
    public ResponseEntity<List<SSHProfile>> getAllProfiles() {
        log.info("Getting all SSH profiles");
        return ResponseEntity.ok(sshProfileService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SSHProfile> getProfile(@PathVariable Long id) {
        log.info("Getting SSH profile with id: {}", id);
        SSHProfile profile = sshProfileService.findById(id);
        return ResponseEntity.ok(profile);
    }

    @PostMapping
    public ResponseEntity<SSHProfile> createProfile(@RequestBody SSHProfile profile) {
        log.info("Creating new SSH profile: {}", profile.getNickname());
        return ResponseEntity.ok(sshProfileService.createProfile(profile));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SSHProfile> updateProfile(
            @PathVariable Long id,
            @RequestBody SSHProfile updatedProfile) {
        log.info("Updating SSH profile with id: {}", id);
        return ResponseEntity.ok(sshProfileService.updateProfile(id, updatedProfile));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProfile(@PathVariable Long id) {
        log.info("Deleting SSH profile with id: {}", id);
        sshProfileService.deleteProfile(id);
        return ResponseEntity.ok().build();
    }
} 