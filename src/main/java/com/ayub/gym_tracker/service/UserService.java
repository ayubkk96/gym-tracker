package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.request.DailyTargetRequest;
import com.ayub.gym_tracker.dto.request.UserRegistrationRequest;
import com.ayub.gym_tracker.dto.response.UserRegistrationResponse;
import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.entity.DailyTarget;
import com.ayub.gym_tracker.exception.EmailAlreadyExistsException;
import com.ayub.gym_tracker.repository.AppUserRepository;
import com.ayub.gym_tracker.repository.DailyTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final AppUserRepository appUserRepository;
    private final DailyTargetRepository dailyTargetRepository;

    public UserService(
            AppUserRepository appUserRepository,
            DailyTargetRepository dailyTargetRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.dailyTargetRepository = dailyTargetRepository;
    }

    @Transactional
    public UserRegistrationResponse register(
            UserRegistrationRequest request
    ) {
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (appUserRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new EmailAlreadyExistsException(email);
        }

        AppUser user = new AppUser(
                email,
                request.displayName().trim()
        );

        AppUser savedUser = appUserRepository.save(user);

        DailyTargetRequest targets = request.targets();

        DailyTarget dailyTarget = new DailyTarget(
                savedUser,
                targets.calories(),
                targets.proteinG(),
                targets.carbsG(),
                targets.fatG(),
                request.startDate()
        );

        dailyTargetRepository.save(dailyTarget);

        return new UserRegistrationResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getDisplayName(),
                request.startDate()
        );
    }
}