package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.ExerciseRequest;
import com.ayub.gym_tracker.dto.ExerciseResponse;
import com.ayub.gym_tracker.dto.WorkoutRequest;
import com.ayub.gym_tracker.dto.WorkoutResponse;
import com.ayub.gym_tracker.dto.WorkoutSaveResult;
import com.ayub.gym_tracker.dto.WorkoutSetRequest;
import com.ayub.gym_tracker.dto.WorkoutSetResponse;
import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.entity.DailyTarget;
import com.ayub.gym_tracker.entity.ExerciseLog;
import com.ayub.gym_tracker.entity.ExerciseSet;
import com.ayub.gym_tracker.entity.WorkoutSession;
import com.ayub.gym_tracker.repository.DailyTargetRepository;
import com.ayub.gym_tracker.repository.WorkoutSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class WorkoutService {

    private final WorkoutSessionRepository workoutSessionRepository;
    private final DailyTargetRepository dailyTargetRepository;
    private final CurrentUserService currentUserService;

    public WorkoutService(
            WorkoutSessionRepository workoutSessionRepository,
            DailyTargetRepository dailyTargetRepository,
            CurrentUserService currentUserService
    ) {
        this.workoutSessionRepository = workoutSessionRepository;
        this.dailyTargetRepository = dailyTargetRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public WorkoutSaveResult saveWorkout(
            WorkoutRequest request
    ) {
        AppUser user = currentUserService.getCurrentUser();

        int dayNumber = calculateDayNumber(
                user,
                request.date()
        );

        String workoutName = request.workout().trim();

        WorkoutSession existingSession = workoutSessionRepository
                .findByUserIdAndWorkoutDateAndNameIgnoreCase(
                        user.getId(),
                        request.date(),
                        workoutName
                )
                .orElse(null);

        boolean created = existingSession == null;

        WorkoutSession session;

        if (created) {
            session = new WorkoutSession(
                    user,
                    request.date(),
                    dayNumber,
                    workoutName,
                    null
            );
        } else {
            session = existingSession;
            session.updateDayNumber(dayNumber);
            session.clearExercises();

            // Delete the old child rows before inserting replacements.
            workoutSessionRepository.flush();
        }

        addExercises(session, request.exercises());

        WorkoutSession savedSession =
                workoutSessionRepository.save(session);

        return new WorkoutSaveResult(
                savedSession.getId(),
                request.exercises().size(),
                created
        );
    }

    public List<WorkoutResponse> getWorkoutsByDate(
            LocalDate date
    ) {
        AppUser user = currentUserService.getCurrentUser();

        return workoutSessionRepository
                .findByUserIdAndWorkoutDateOrderByIdAsc(
                        user.getId(),
                        date
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private int calculateDayNumber(
            AppUser user,
            LocalDate workoutDate
    ) {
        DailyTarget firstTarget = dailyTargetRepository
                .findFirstByUserIdOrderByEffectiveFromAsc(
                        user.getId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "No targets found for user: "
                                + user.getEmail()
                ));

        LocalDate startDate = firstTarget.getEffectiveFrom();

        long difference = ChronoUnit.DAYS.between(
                startDate,
                workoutDate
        );

        if (difference < 0) {
            throw new IllegalArgumentException(
                    "Date cannot be before " + startDate
            );
        }

        return Math.toIntExact(difference) + 1;
    }

    private void addExercises(
            WorkoutSession session,
            List<ExerciseRequest> exerciseRequests
    ) {
        for (int exerciseIndex = 0;
             exerciseIndex < exerciseRequests.size();
             exerciseIndex++) {

            ExerciseRequest exerciseRequest =
                    exerciseRequests.get(exerciseIndex);

            ExerciseLog exerciseLog = new ExerciseLog(
                    exerciseRequest.name().trim(),
                    exerciseIndex + 1,
                    cleanNotes(exerciseRequest.notes())
            );

            addSets(exerciseLog, exerciseRequest.sets());
            session.addExercise(exerciseLog);
        }
    }

    private void addSets(
            ExerciseLog exerciseLog,
            List<WorkoutSetRequest> setRequests
    ) {
        for (int setIndex = 0;
             setIndex < setRequests.size();
             setIndex++) {

            WorkoutSetRequest setRequest =
                    setRequests.get(setIndex);

            ExerciseSet exerciseSet = new ExerciseSet(
                    setIndex + 1,
                    setRequest.weightKg(),
                    setRequest.reps()
            );

            exerciseLog.addSet(exerciseSet);
        }
    }

    private WorkoutResponse toResponse(
            WorkoutSession session
    ) {
        List<ExerciseResponse> exercises = session
                .getExercises()
                .stream()
                .map(this::toExerciseResponse)
                .toList();

        return new WorkoutResponse(
                session.getName(),
                session.getDayNumber(),
                exercises
        );
    }

    private ExerciseResponse toExerciseResponse(
            ExerciseLog exercise
    ) {
        List<WorkoutSetResponse> sets = exercise
                .getExerciseSets()
                .stream()
                .map(set -> new WorkoutSetResponse(
                        set.getWeightKg(),
                        set.getReps()
                ))
                .toList();

        int totalReps = exercise
                .getExerciseSets()
                .stream()
                .mapToInt(ExerciseSet::getReps)
                .sum();

        return new ExerciseResponse(
                exercise.getName(),
                sets,
                totalReps,
                exercise.getNotes()
        );
    }

    private String cleanNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }

        return notes.trim();
    }
}