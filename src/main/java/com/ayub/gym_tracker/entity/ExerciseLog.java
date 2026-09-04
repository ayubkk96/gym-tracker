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

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exercise_logs")
public class ExerciseLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_session_id", nullable = false)
    private WorkoutSession workoutSession;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "exercise_order", nullable = false)
    private Integer exerciseOrder;

    @Column(length = 500)
    private String notes;

    @OneToMany(
            mappedBy = "exerciseLog",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("setNumber ASC")
    private List<ExerciseSet> exerciseSets = new ArrayList<>();

    protected ExerciseLog() {
    }

    public ExerciseLog(
            String name,
            Integer exerciseOrder,
            String notes
    ) {
        this.name = name;
        this.exerciseOrder = exerciseOrder;
        this.notes = notes;
    }

    void attachTo(WorkoutSession workoutSession) {
        this.workoutSession = workoutSession;
    }

    public void addSet(ExerciseSet exerciseSet) {
        exerciseSet.attachTo(this);
        exerciseSets.add(exerciseSet);
    }

    public Long getId() {
        return id;
    }

    public WorkoutSession getWorkoutSession() {
        return workoutSession;
    }

    public String getName() {
        return name;
    }

    public Integer getExerciseOrder() {
        return exerciseOrder;
    }

    public String getNotes() {
        return notes;
    }

    public List<ExerciseSet> getExerciseSets() {
        return exerciseSets;
    }
}