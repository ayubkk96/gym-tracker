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
public class EmailVerificationMailService {
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationMailService.class);

    private final EmailVerificationService tokens;
    private final ThreadPoolTaskExecutor executor;
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String origin;
    private final String from;

    public EmailVerificationMailService(
            EmailVerificationService tokens,
            ThreadPoolTaskExecutor recoveryExecutor,
            ObjectProvider<JavaMailSender> senders,
            @Value("${tracker.verification.enabled:false}") boolean enabled,
            @Value("${tracker.verification.public-url:}") String origin,
            @Value("${tracker.verification.from:}") String from
    ) {
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
                throw new IllegalStateException(
                        "Email verification requires SMTP, a sender address and an HTTPS origin without a path."
                );
            }
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public void request(String email) {
        executor.execute(() -> {
            try {
                tokens.issue(email).ifPresent(delivery -> {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(from);
                    message.setTo(delivery.email());
                    message.setSubject("Verify your Gym Tracker email");
                    message.setText("Verify your email address to activate your Gym Tracker account:\n\n"
                            + origin + "/verify-email.html#token=" + delivery.token()
                            + "\n\nThis link expires in 24 hours. If you did not create this account, ignore this email.");
                    sender.send(message);
                });
            } catch (RuntimeException exception) {
                // Mail exceptions may contain addresses, credentials or message bodies.
                log.error("event=verification_delivery_failed exceptionType={}",
                        exception.getClass().getSimpleName());
            }
        });
    }
}
