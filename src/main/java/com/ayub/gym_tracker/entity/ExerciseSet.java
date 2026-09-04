package com.ayub.gym_tracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "exercise_sets")
public class ExerciseSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_log_id", nullable = false)
    private ExerciseLog exerciseLog;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private Integer reps;

    protected ExerciseSet() {
    }

    public ExerciseSet(
            Integer setNumber,
            BigDecimal weightKg,
            Integer reps
    ) {
        this.setNumber = setNumber;
        this.weightKg = weightKg;
        this.reps = reps;
    }

    void attachTo(ExerciseLog exerciseLog) {
        this.exerciseLog = exerciseLog;
    }

    public Long getId() {
        return id;
    }

    public ExerciseLog getExerciseLog() {
        return exerciseLog;
    }

    public Integer getSetNumber() {
        return setNumber;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Integer getReps() {
        return reps;
    }
}