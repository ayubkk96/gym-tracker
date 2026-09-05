package com.ayub.gym_tracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import java.net.URI;

@Service
public class RecoveryMailService {
    private static final Logger log = LoggerFactory.getLogger(RecoveryMailService.class);
    private final PasswordResetService tokens;
    private final ThreadPoolTaskExecutor executor;
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String origin;
    private final String from;

    public RecoveryMailService(PasswordResetService tokens, ThreadPoolTaskExecutor recoveryExecutor,
                               ObjectProvider<JavaMailSender> senders,
                               @Value("${tracker.recovery.enabled:false}") boolean enabled,
                               @Value("${tracker.recovery.public-url:}") String origin,
                               @Value("${tracker.recovery.from:}") String from) {
        this.tokens = tokens;
        this.executor = recoveryExecutor;
        this.sender = senders.getIfAvailable();
        this.enabled = enabled;
        this.origin = origin.replaceAll("/+$", "");
        this.from = from;
        if (enabled) {
            URI uri = URI.create(this.origin);
            if (sender == null || from.isBlank() || from.contains("\r") || from.contains("\n")
                    || !"https".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getPath() == null || uri.getPath().isEmpty())) {
                throw new IllegalStateException("Recovery requires SMTP, a sender address and an HTTPS origin without a path.");
            }
        }
    }

    public boolean enabled() { return enabled; }

    public void request(String email) {
        // Account lookup and network delivery are off the response path for all emails.
        // Bounded queue + global request limits avoid unbounded work under abuse.
        executor.execute(() -> {
            try {
                tokens.issue(email).ifPresent(delivery -> {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(from);
                    message.setTo(delivery.email());
                    message.setSubject("Reset your Gym Tracker password");
                    message.setText("Use this single-use link within 15 minutes to reset your password:\n\n"
                            + origin + "/reset-password.html#token=" + delivery.token()
                            + "\n\nIf you did not request this, ignore this email. Your password has not changed.");
                    sender.send(message);
                });
            } catch (RuntimeException exception) {
                // Mail exceptions may contain addresses, SMTP credentials or message bodies.
                log.error("event=recovery_delivery_failed exceptionType={}", exception.getClass().getSimpleName());
            }
        });
    }
}
