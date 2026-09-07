package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.dto.request.UserRegistrationRequest;
import com.ayub.gym_tracker.dto.response.UserRegistrationResponse;
import com.ayub.gym_tracker.service.EmailVerificationMailService;
import com.ayub.gym_tracker.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final EmailVerificationMailService verificationMail;

    public UserController(
            UserService userService,
            EmailVerificationMailService verificationMail
    ) {
        this.userService = userService;
        this.verificationMail = verificationMail;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserRegistrationResponse register(
            @Valid @RequestBody UserRegistrationRequest request
    ) {
        boolean verificationRequired = verificationMail.enabled();
        UserRegistrationResponse response = userService.register(
                request,
                verificationRequired
        );

        if (verificationRequired) {
            try {
                verificationMail.request(response.email());
            } catch (TaskRejectedException exception) {
                // The account remains pending and the public resend endpoint can issue a new email.
                log.error("event=verification_queue_rejected");
            }
        }

        return response;
    }
}
