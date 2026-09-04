package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;
    private final String currentUserEmail;

    public CurrentUserService(
            AppUserRepository appUserRepository,
            @Value("${tracker.current-user-email}") String currentUserEmail
    ) {
        this.appUserRepository = appUserRepository;
        this.currentUserEmail = currentUserEmail;
    }

    public AppUser getCurrentUser() {
        return appUserRepository
                .findByEmailIgnoreCase(currentUserEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "Current user not found: " + currentUserEmail
                ));
    }
}