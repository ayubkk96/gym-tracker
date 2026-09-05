package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.request.ExerciseRequest;
import com.ayub.gym_tracker.dto.request.WorkoutRequest;
import com.ayub.gym_tracker.dto.request.WorkoutSetRequest;
import com.ayub.gym_tracker.dto.response.ExerciseResponse;
import com.ayub.gym_tracker.dto.response.WorkoutResponse;
import com.ayub.gym_tracker.dto.response.WorkoutSetResponse;
import com.ayub.gym_tracker.dto.result.WorkoutSaveResult;
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

        Integer dayNumber = calculateDayNumber(
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
                    cleanNotes(request.notes())
            );
        } else {
            session = existingSession;
            session.updateDayNumber(dayNumber);
            session.updateNotes(cleanNotes(request.notes()));
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

    @Transactional
    public WorkoutSaveResult replaceWorkout(String originalName, LocalDate originalDate, WorkoutRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        WorkoutSession original = workoutSessionRepository
                .findByUserIdAndWorkoutDateAndNameIgnoreCase(user.getId(), originalDate, originalName.trim())
                .orElseThrow(() -> new IllegalArgumentException("Original workout no longer exists. Reload the dashboard."));
        String name = request.workout().trim();
        if (name.equalsIgnoreCase("Rest") && !request.exercises().isEmpty())
            throw new IllegalArgumentException("A rest entry cannot contain exercises.");
        WorkoutSession target = workoutSessionRepository
                .findByUserIdAndWorkoutDateAndNameIgnoreCase(user.getId(), request.date(), name).orElse(null);
        if (target != null && !target.getId().equals(original.getId())) {
            // Collapse an existing empty Rest marker, including duplicates created by the old editor.
            if (!name.equalsIgnoreCase("Rest") || !target.getExercises().isEmpty())
                throw new IllegalArgumentException("A workout with that name already exists on this date.");
            workoutSessionRepository.delete(target);
            workoutSessionRepository.flush();
        }
        original.renameAndMove(name, request.date());
        workoutSessionRepository.flush();
        return saveWorkout(request);
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

    private Integer calculateDayNumber(
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

        return difference < 0 ? null : Math.toIntExact(difference) + 1;
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
                exercises,
                session.getNotes()
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
