package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LegacyAccountBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            LegacyAccountBootstrap.class
    );

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapEmail;
    private final String bootstrapPassword;

    public LegacyAccountBootstrap(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${tracker.bootstrap-email:}")
            String bootstrapEmail,
            @Value("${tracker.bootstrap-password:}")
            String bootstrapPassword
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        boolean emailMissing = bootstrapEmail.isBlank();
        boolean passwordMissing = bootstrapPassword.isBlank();

        if (emailMissing && passwordMissing) {
            return;
        }

        if (emailMissing || passwordMissing) {
            throw new IllegalStateException(
                    "TRACKER_BOOTSTRAP_EMAIL and "
                            + "TRACKER_BOOTSTRAP_PASSWORD must be set together."
            );
        }

        if (bootstrapPassword.length() < 12
                || bootstrapPassword.length() > 64) {
            throw new IllegalStateException(
                    "TRACKER_BOOTSTRAP_PASSWORD must contain "
                            + "between 12 and 64 characters."
            );
        }

        AppUser user = appUserRepository
                .findByEmailIgnoreCase(bootstrapEmail.trim())
                .orElseThrow(() -> new IllegalStateException(
                        "Bootstrap user does not exist."
                ));

        if (user.hasPassword()) {
            LOGGER.info(
                    "Bootstrap account already has a password; "
                            + "no changes were made."
            );
            return;
        }

        user.updatePasswordHash(
                passwordEncoder.encode(bootstrapPassword)
        );
        appUserRepository.save(user);

        LOGGER.info(
                "Password created for the existing bootstrap account. "
                        + "Remove the bootstrap environment variables."
        );
    }
}
