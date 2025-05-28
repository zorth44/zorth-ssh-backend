package com.zorth.ssh.service;

import com.zorth.ssh.entity.SSHProfile;
import com.zorth.ssh.repository.SSHProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SSHProfileService {

    private final SSHProfileRepository sshProfileRepository;

    public List<SSHProfile> findAll() {
        return sshProfileRepository.findAll();
    }

    public SSHProfile findById(Long id) {
        return sshProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SSH Profile not found with id: " + id));
    }

    @Transactional
    public SSHProfile createProfile(SSHProfile profile) {
        return sshProfileRepository.save(profile);
    }

    @Transactional
    public SSHProfile updateProfile(Long id, SSHProfile updatedProfile) {
        SSHProfile existingProfile = findById(id);
        
        // Update fields
        existingProfile.setNickname(updatedProfile.getNickname());
        existingProfile.setHost(updatedProfile.getHost());
        existingProfile.setPort(updatedProfile.getPort());
        existingProfile.setUsername(updatedProfile.getUsername());
        existingProfile.setAuthType(updatedProfile.getAuthType());
        
        if (updatedProfile.getAuthType() == SSHProfile.AuthType.PASSWORD) {
            existingProfile.setEncryptedPassword(updatedProfile.getEncryptedPassword());
            existingProfile.setEncryptedPrivateKey(null);
            existingProfile.setKeyPassphraseEncrypted(null);
        } else {
            existingProfile.setEncryptedPassword(null);
            existingProfile.setEncryptedPrivateKey(updatedProfile.getEncryptedPrivateKey());
            existingProfile.setKeyPassphraseEncrypted(updatedProfile.getKeyPassphraseEncrypted());
        }

        return sshProfileRepository.save(existingProfile);
    }

    @Transactional
    public void deleteProfile(Long id) {
        sshProfileRepository.deleteById(id);
    }
} 