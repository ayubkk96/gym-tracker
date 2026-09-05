const dateInput = document.querySelector("#dashboard-date");
const loadButton = document.querySelector("#load-dashboard");
const statusMessage = document.querySelector("#status-message");
const workoutContainer = document.querySelector("#workout-container");
const nutritionDialog =
    document.querySelector("#nutrition-dialog");

const nutritionForm =
    document.querySelector("#nutrition-form");

const openNutritionButton =
    document.querySelector("#open-nutrition-form");

const closeNutritionButton =
    document.querySelector("#close-nutrition-form");

const cancelNutritionButton =
    document.querySelector("#cancel-nutrition-form");

const saveNutritionButton =
    document.querySelector("#save-nutrition");

const nutritionFormStatus =
    document.querySelector("#nutrition-form-status");

let currentDashboard = null;

dateInput.value = getLocalDate();

loadButton.addEventListener("click", loadDashboard);

loadDashboard();

async function loadDashboard() {
    const date = dateInput.value;

    if (!date) {
        showError("Please choose a date.");
        return;
    }

    setLoading(true);

    try {
        const response = await fetch(
            `/api/dashboard?date=${encodeURIComponent(date)}`
        );

        if (!response.ok) {
            throw new Error(
                `Dashboard request failed: ${response.status}`
            );
        }

        const dashboard = await response.json();

        renderDashboard(dashboard);
        statusMessage.textContent = "";
        statusMessage.classList.remove("error", "success");
    } catch (error) {
        console.error(error);
        showError("Could not load your dashboard.");
    } finally {
        setLoading(false);
    }
}

function renderDashboard(dashboard) {
    currentDashboard = dashboard;

    document.querySelector("#day-title").textContent =
        new Intl.DateTimeFormat("en-GB", {
            weekday: "long",
            day: "numeric",
            month: "long",
            year: "numeric"
        }).format(
            new Date(`${dashboard.date}T00:00:00`)
        );

    renderNutrition(
        dashboard.nutrition,
        dashboard.targets
    );

    renderWorkouts(dashboard.workouts);
}

function renderNutrition(nutrition, targets) {
    const values = nutrition ?? {
        calories: 0,
        proteinG: 0,
        carbsG: 0,
        fatG: 0,
        weightKg: null,
        notes: null
    };

    updateMacro(
        "calories",
        values.calories,
        targets.calories,
        ""
    );

    updateMacro(
        "protein",
        values.proteinG,
        targets.proteinG,
        "g"
    );

    updateMacro(
        "carbs",
        values.carbsG,
        targets.carbsG,
        "g"
    );

    updateMacro(
        "fat",
        values.fatG,
        targets.fatG,
        "g"
    );

    document.querySelector("#weight-value").textContent =
        values.weightKg == null
            ? "Weight: not logged"
            : `Weight: ${values.weightKg}kg`;

    document.querySelector("#nutrition-notes").textContent =
        values.notes ?? "";
}

function updateMacro(name, current, target, unit) {
    document.querySelector(`#${name}-value`).textContent =
        `${current}${unit} / ${target}${unit}`;

    const progress =
        document.querySelector(`#${name}-progress`);

    progress.max = target;
    progress.value = current;
}

function renderWorkouts(workouts) {
    workoutContainer.replaceChildren();

    if (!workouts || workouts.length === 0) {
        const emptyState = document.createElement("div");

        emptyState.className = "empty-state";
        emptyState.textContent =
            "No workout recorded for this date.";

        workoutContainer.append(emptyState);
        return;
    }

    for (const workout of workouts) {
        workoutContainer.append(createWorkoutCard(workout));
    }
}

function createWorkoutCard(workout) {
    const card = document.createElement("article");
    card.className = "workout-card";

    const title = document.createElement("h3");
    title.className = "workout-title";
    title.textContent = workout.name;

    card.append(title);

    for (const exercise of workout.exercises) {
        card.append(createExercise(exercise));
    }

    return card;
}

function createExercise(exercise) {
    const container = document.createElement("div");
    container.className = "exercise";

    const heading = document.createElement("div");
    heading.className = "exercise-heading";

    const name = document.createElement("h3");
    name.textContent = exercise.name;

    const totalReps = document.createElement("span");
    totalReps.className = "total-reps";
    totalReps.textContent =
        `${exercise.totalReps} total reps`;

    heading.append(name, totalReps);

    const setList = document.createElement("div");
    setList.className = "set-list";

    exercise.sets.forEach((set, index) => {
        const setElement = document.createElement("span");
        setElement.className = "set";

        const weight = set.weightKg == null
            ? "BW"
            : `${set.weightKg}kg`;

        setElement.textContent =
            `Set ${index + 1}: ${weight} × ${set.reps}`;

        setList.append(setElement);
    });

    container.append(heading, setList);

    if (exercise.notes) {
        const notes = document.createElement("p");
        notes.className = "exercise-note";
        notes.textContent = exercise.notes;

        container.append(notes);
    }

    return container;
}

function setLoading(loading) {
    loadButton.disabled = loading;
    loadButton.textContent = loading ? "Loading…" : "Load";

    if (loading) {
        statusMessage.textContent = "Loading dashboard…";
        statusMessage.classList.remove("error");
    }
}

function showError(message) {
    statusMessage.textContent = message;
    statusMessage.classList.add("error");
}

function getLocalDate() {
    const now = new Date();
    const offset = now.getTimezoneOffset() * 60_000;

    return new Date(now.getTime() - offset)
        .toISOString()
        .slice(0, 10);
}

openNutritionButton.addEventListener(
    "click",
    openNutritionForm
);

closeNutritionButton.addEventListener(
    "click",
    () => nutritionDialog.close()
);

cancelNutritionButton.addEventListener(
    "click",
    () => nutritionDialog.close()
);

nutritionForm.addEventListener(
    "submit",
    saveNutrition
);

function openNutritionForm() {
    nutritionForm.reset();
    clearNutritionFormStatus();

    nutritionForm.elements.date.value =
        dateInput.value;

    const dashboardMatchesSelectedDate =
        currentDashboard
        && currentDashboard.date === dateInput.value;

    if (dashboardMatchesSelectedDate
            && currentDashboard.nutrition) {
        populateNutritionForm(
            currentDashboard.nutrition
        );
    }

    nutritionDialog.showModal();
}

function populateNutritionForm(nutrition) {
    nutritionForm.elements.calories.value =
        nutrition.calories;

    nutritionForm.elements.proteinG.value =
        nutrition.proteinG;

    nutritionForm.elements.carbsG.value =
        nutrition.carbsG;

    nutritionForm.elements.fatG.value =
        nutrition.fatG;

    nutritionForm.elements.weightKg.value =
        nutrition.weightKg ?? "";

    nutritionForm.elements.notes.value =
        nutrition.notes ?? "";
}

async function saveNutrition(event) {
    event.preventDefault();

    if (!nutritionForm.reportValidity()) {
        return;
    }

    const formData = new FormData(nutritionForm);

    const payload = {
        date: formData.get("date"),
        calories: Number(formData.get("calories")),
        proteinG: Number(formData.get("proteinG")),
        carbsG: Number(formData.get("carbsG")),
        fatG: Number(formData.get("fatG")),
        weightKg: optionalNumber(
            formData.get("weightKg")
        ),
        notes: optionalText(
            formData.get("notes")
        )
    };

    setNutritionSaving(true);
    clearNutritionFormStatus();

    try {
        const response = await fetch("/api/nutrition", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            throw new Error(
                `Nutrition request failed: ${response.status}`
            );
        }

        const result = await response.json();

        nutritionDialog.close();

        dateInput.value = payload.date;

        await loadDashboard();

        showSuccess(
            result.action === "updated"
                ? "Nutrition updated successfully."
                : "Nutrition saved successfully."
        );
    } catch (error) {
        console.error(error);

        nutritionFormStatus.textContent =
            "Could not save nutrition.";

        nutritionFormStatus.classList.add("error");
    } finally {
        setNutritionSaving(false);
    }
}

function optionalNumber(value) {
    if (value == null || value.trim() === "") {
        return null;
    }

    return Number(value);
}

function optionalText(value) {
    if (value == null || value.trim() === "") {
        return null;
    }

    return value.trim();
}

function setNutritionSaving(saving) {
    saveNutritionButton.disabled = saving;

    saveNutritionButton.textContent = saving
        ? "Saving…"
        : "Save Nutrition";
}

function clearNutritionFormStatus() {
    nutritionFormStatus.textContent = "";
    nutritionFormStatus.classList.remove("error");
}

function showSuccess(message) {
    statusMessage.textContent = message;
    statusMessage.classList.remove("error");
    statusMessage.classList.add("success");
}

function showError(message) {
    statusMessage.textContent = message;
    statusMessage.classList.remove("success");
    statusMessage.classList.add("error");
}