package com.ayub.gym_tracker.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workout_sessions")
public class WorkoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "workout_date", nullable = false)
    private LocalDate workoutDate;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(
            mappedBy = "workoutSession",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("exerciseOrder ASC")
    private List<ExerciseLog> exercises = new ArrayList<>();

    protected WorkoutSession() {
    }

    public WorkoutSession(
            AppUser user,
            LocalDate workoutDate,
            Integer dayNumber,
            String name,
            String notes
    ) {
        this.user = user;
        this.workoutDate = workoutDate;
        this.dayNumber = dayNumber;
        this.name = name;
        this.notes = notes;
    }

    public void updateDayNumber(Integer dayNumber) {
        this.dayNumber = dayNumber;
    }

    public void addExercise(ExerciseLog exercise) {
        exercise.attachTo(this);
        exercises.add(exercise);
    }

    public void clearExercises() {
        exercises.clear();
    }

    public void updateNotes(String notes) {
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public LocalDate getWorkoutDate() {
        return workoutDate;
    }

    public Integer getDayNumber() {
        return dayNumber;
    }

    public String getName() {
        return name;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ExerciseLog> getExercises() {
        return exercises;
    }
}