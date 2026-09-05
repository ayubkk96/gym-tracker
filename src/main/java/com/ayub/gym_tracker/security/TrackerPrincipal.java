package com.ayub.gym_tracker.security;

import com.ayub.gym_tracker.entity.AppUser;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public class TrackerPrincipal extends User {
    private final Long userId;
    private final long passwordVersion;

    public TrackerPrincipal(AppUser user) {
        super(user.getEmail(), user.getPasswordHash(), AuthorityUtils.createAuthorityList("ROLE_USER"));
        userId = user.getId();
        passwordVersion = user.getPasswordVersion();
    }

    public Long userId() { return userId; }
    public long passwordVersion() { return passwordVersion; }
}
