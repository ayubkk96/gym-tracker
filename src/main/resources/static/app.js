const dateInput = document.querySelector("#dashboard-date");
const loadButton = document.querySelector("#load-dashboard");
const statusMessage = document.querySelector("#status-message");
const workoutContainer = document.querySelector("#workout-container");
const recentHistoryContainer =
    document.querySelector("#recent-history-container");
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

const workoutDialog =
    document.querySelector("#workout-dialog");

const workoutForm =
    document.querySelector("#workout-form");

const openWorkoutButton =
    document.querySelector("#open-workout-form");

const closeWorkoutButton =
    document.querySelector("#close-workout-form");

const cancelWorkoutButton =
    document.querySelector("#cancel-workout-form");

const saveWorkoutButton =
    document.querySelector("#save-workout");

const addExerciseButton =
    document.querySelector("#add-exercise");

const restWorkoutCheckbox =
    document.querySelector("#rest-workout");

const exerciseEditorSection =
    document.querySelector("#exercise-editor-section");

const exerciseEditors =
    document.querySelector("#exercise-editors");

const exerciseEditorTemplate =
    document.querySelector("#exercise-editor-template");

const setEditorTemplate =
    document.querySelector("#set-editor-template");

const workoutFormStatus =
    document.querySelector("#workout-form-status");
const currentUser =
    document.querySelector("#current-user");
const logoutButton =
    document.querySelector("#logout-button");

let currentDashboard = null;
let editingWorkoutName = null;
let csrfToken = null;
let csrfHeaderName = "X-CSRF-TOKEN";
let dashboardRequestId = 0;

dateInput.value = getLocalDate();

loadButton.addEventListener("click", loadDashboard);
dateInput.addEventListener("change", loadDashboard);
document.querySelector("#previous-date").addEventListener("click", () => moveDate(-1));
document.querySelector("#next-date").addEventListener("click", () => moveDate(1));
document.querySelector("#today-date").addEventListener("click", () => {
    dateInput.value = getLocalDate();
    loadDashboard();
});
logoutButton.addEventListener("click", logout);

openNutritionButton.disabled = true;
openWorkoutButton.disabled = true;
initialiseDashboard();

function moveDate(offset) {
    const date = new Date(`${dateInput.value || getLocalDate()}T12:00:00`);
    date.setDate(date.getDate() + offset);
    dateInput.value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    loadDashboard();
}

async function initialiseDashboard() {
    try {
        const response = await fetch("/api/auth/session");

        if (!response.ok) {
            throw new Error(
                `Session request failed: ${response.status}`
            );
        }

        const session = await response.json();

        if (!session.authenticated) {
            redirectToLogin();
            return;
        }

        csrfToken = session.csrfToken;
        csrfHeaderName = session.csrfHeaderName;
        currentUser.textContent =
            session.displayName || session.email;

        await loadDashboard();
    } catch (error) {
        console.error(error);
        showError("Could not initialise your dashboard.");
    }
}

async function loadDashboard() {
    const requestId = ++dashboardRequestId;
    const date = dateInput.value;

    if (!date) {
        setLoading(false);
        showError("Please choose a date.");
        return;
    }

    setLoading(true);

    try {
        const response = await fetch(
            `/api/dashboard?date=${encodeURIComponent(date)}`
        );

        requireAuthenticated(response);

        if (!response.ok) {
            throw new Error(
                `Dashboard request failed: ${response.status}`
            );
        }

        const dashboard = await response.json();

        // A slower, earlier response must not replace the selected date.
        if (requestId !== dashboardRequestId) return;

        renderDashboard(dashboard);
        statusMessage.textContent = "";
        statusMessage.classList.remove("error", "success");
    } catch (error) {
        if (requestId !== dashboardRequestId) return;
        console.error(error);
        showError(`Could not load ${date}. ${currentDashboard ? "The previous snapshot is still shown. " : ""}Select View to retry.`);
    } finally {
        if (requestId === dashboardRequestId) setLoading(false);
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
    renderWeeklySummary(dashboard.weeklySummary);
    renderRecentHistory(dashboard.recentHistory);
}

function renderNutrition(nutrition, targets) {
    const values = nutrition ?? {
        calories: null,
        proteinG: null,
        carbsG: null,
        fatG: null,
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
        nutrition ? (values.notes ?? "") : "No nutrition logged for this date yet. Add your intake when you're ready.";
    openNutritionButton.textContent = nutrition ? "Edit nutrition" : "+ Log nutrition";
}

function updateMacro(name, current, target, unit) {
    document.querySelector(`#${name}-value`).textContent =
        current == null ? "—" : `${new Intl.NumberFormat("en-GB").format(current)}${unit}`;
    setText(`${name}-target`, `Daily target ${new Intl.NumberFormat("en-GB").format(target)}${unit || " kcal"}`);

    const progress =
        document.querySelector(`#${name}-progress`);

    progress.max = target > 0 ? target : 1;
    progress.value = current ?? 0;
    progress.setAttribute("aria-valuetext", current == null ? "Not logged" : `${current}${unit} of ${target}${unit}`);
}

function renderWeeklySummary(summary) {
    if (!summary) {
        setText("average-calories", "—");
        setText("average-protein", "—");
        setText("average-carbs", "—");
        setText("average-fat", "—");
        setText("training-sessions", "—");
        setText("nutrition-days-logged", "—");
        setText("weekly-period", "—");
        return;
    }

    setText(
        "average-calories",
        formatAverage(summary.averageCalories, " kcal")
    );
    setText(
        "average-protein",
        formatAverage(summary.averageProteinG, "g")
    );
    setText(
        "average-carbs",
        formatAverage(summary.averageCarbsG, "g")
    );
    setText(
        "average-fat",
        formatAverage(summary.averageFatG, "g")
    );
    setText(
        "training-sessions",
        String(summary.trainingSessions)
    );
    setText(
        "nutrition-days-logged",
        `${summary.nutritionDaysLogged} of 7`
    );
    setText(
        "weekly-period",
        `${formatShortDate(summary.fromDate)} – `
            + formatShortDate(summary.toDate)
    );
}

function renderRecentHistory(history) {
    recentHistoryContainer.replaceChildren();

    if (!history || history.length === 0) {
        const emptyState = document.createElement("div");
        emptyState.className = "empty-state";
        emptyState.textContent =
            "No entries recorded in the last seven days.";

        recentHistoryContainer.append(emptyState);
        return;
    }

    const historyList = document.createElement("div");
    historyList.className = "history-list";

    for (const entry of history) {
        historyList.append(createHistoryRow(entry));
    }

    recentHistoryContainer.append(historyList);
}

function createHistoryRow(entry) {
    const row = document.createElement("button");
    row.className = "history-row";
    row.type = "button";
    if (entry.date === currentDashboard?.date) row.setAttribute("aria-current", "date");

    row.addEventListener("click", async () => {
        dateInput.value = entry.date;
        await loadDashboard();
        if (currentDashboard?.date === entry.date) {
            document.querySelector("#day-title").focus();
        }
    });

    const date = document.createElement("strong");
    date.className = "history-date";
    date.textContent = formatHistoryDate(entry.date);

    const details = document.createElement("div");
    details.className = "history-details";

    const workout = document.createElement("strong");
    workout.textContent = entry.workouts.length > 0
        ? entry.workouts.join(" + ")
        : "No workout";

    const nutrition = document.createElement("span");
    nutrition.className = "history-meta";

    const nutritionParts = [];

    if (entry.calories != null) {
        nutritionParts.push(`${entry.calories} kcal`);
    }

    if (entry.proteinG != null) {
        nutritionParts.push(`${entry.proteinG}g protein`);
    }

    if (entry.weightKg != null) {
        nutritionParts.push(`${entry.weightKg}kg`);
    }

    nutrition.textContent = nutritionParts.length > 0
        ? nutritionParts.join(" · ")
        : "Nutrition not recorded";

    details.append(workout, nutrition);

    const arrow = document.createElement("span");
    arrow.className = "history-arrow";
    arrow.textContent = "›";
    arrow.setAttribute("aria-hidden", "true");

    row.append(date, details, arrow);
    return row;
}

function setText(id, value) {
    document.querySelector(`#${id}`).textContent = value;
}

function formatAverage(value, unit) {
    return value == null ? "—" : `${value}${unit}`;
}

function formatShortDate(value) {
    return new Intl.DateTimeFormat("en-GB", {
        day: "numeric",
        month: "short"
    }).format(new Date(`${value}T00:00:00`));
}

function formatHistoryDate(value) {
    return new Intl.DateTimeFormat("en-GB", {
        weekday: "short",
        day: "numeric",
        month: "short"
    }).format(new Date(`${value}T00:00:00`));
}

function renderWorkouts(workouts) {
    workoutContainer.replaceChildren();

    if (!workouts || workouts.length === 0) {
        const emptyState = document.createElement("div");

        emptyState.className = "empty-state";
        const title = document.createElement("strong");
        title.textContent = "A fresh page for your training";
        const hint = document.createElement("span");
        hint.textContent = "Log a session or record a rest day for this date.";
        const action = document.createElement("button");
        action.type = "button";
        action.className = "secondary-button";
        action.dataset.dashboardAction = "true";
        action.textContent = "+ Log workout";
        action.addEventListener("click", () => openWorkoutForm());
        emptyState.append(title, hint, action);

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

    const titleRow = document.createElement("div");
    titleRow.className = "workout-title-row";

    const title = document.createElement("h3");
    title.className = "workout-title";
    title.textContent = workout.name;

    const editButton = document.createElement("button");
    editButton.className = "secondary-button compact-button";
    editButton.type = "button";
    editButton.textContent = "Edit";
    editButton.dataset.dashboardAction = "true";
    editButton.setAttribute("aria-label", `Edit ${workout.name}`);
    editButton.addEventListener(
        "click",
        () => openWorkoutForm(workout)
    );

    titleRow.append(title, editButton);
    card.append(titleRow);

    if (workout.notes) {
        const workoutNotes = document.createElement("p");
        workoutNotes.className = "workout-note";
        workoutNotes.textContent = workout.notes;
        card.append(workoutNotes);
    }

    if (workout.exercises.length === 0) {
        const emptyWorkout = document.createElement("p");
        emptyWorkout.className = "workout-empty";
        emptyWorkout.textContent =
            workout.name.toLowerCase() === "rest"
                ? "Recovery day"
                : "No exercises recorded.";

        card.append(emptyWorkout);
        return card;
    }

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

        const label = document.createElement("span");
        label.className = "set-label";
        label.textContent = `Set ${index + 1}`;
        const value = document.createElement("span");
        value.textContent = `${weight} × ${set.reps}`;
        setElement.append(label, value);

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
    loadButton.textContent = loading ? "Wait…" : "View";
    document.querySelector("#dashboard-content").setAttribute("aria-busy", String(loading));
    const unavailable = loading || !currentDashboard || currentDashboard.date !== dateInput.value;
    openNutritionButton.disabled = unavailable;
    openWorkoutButton.disabled = unavailable;
    document.querySelectorAll("[data-dashboard-action]").forEach(button => {
        button.disabled = unavailable;
    });

    if (loading) {
        statusMessage.textContent = "Loading dashboard…";
        statusMessage.classList.remove("error", "success");
    }
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
            headers: mutationHeaders(),
            body: JSON.stringify(payload)
        });

        requireAuthenticated(response, true);

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

openWorkoutButton.addEventListener(
    "click",
    () => openWorkoutForm()
);

closeWorkoutButton.addEventListener(
    "click",
    () => workoutDialog.close()
);

cancelWorkoutButton.addEventListener(
    "click",
    () => workoutDialog.close()
);

addExerciseButton.addEventListener(
    "click",
    () => addExerciseEditor()
);

restWorkoutCheckbox.addEventListener(
    "change",
    updateRestWorkoutState
);

workoutForm.addEventListener(
    "submit",
    saveWorkout
);

function openWorkoutForm(workout = null) {
    workoutForm.reset();
    exerciseEditors.replaceChildren();
    clearWorkoutFormStatus();

    editingWorkoutName = workout?.name ?? null;

    document.querySelector("#workout-form-title").textContent =
        workout ? "Edit Workout" : "Log Workout";

    workoutForm.elements.date.value = dateInput.value;
    workoutForm.elements.workout.readOnly = workout != null;

    if (workout) {
        workoutForm.elements.workout.value = workout.name;
        workoutForm.elements.notes.value = workout.notes ?? "";

        restWorkoutCheckbox.checked =
            workout.name.toLowerCase() === "rest";

        for (const exercise of workout.exercises) {
            addExerciseEditor(exercise);
        }
    } else {
        addExerciseEditor();
    }

    updateRestWorkoutState();
    workoutDialog.showModal();
}

function addExerciseEditor(exercise = null) {
    if (exerciseEditors.children.length >= 20) {
        showWorkoutFormError(
            "A workout can contain up to 20 exercises."
        );
        return;
    }

    const editor = exerciseEditorTemplate.content
        .firstElementChild
        .cloneNode(true);

    editor.querySelector(".exercise-name").value =
        exercise?.name ?? "";

    editor.querySelector(".exercise-notes").value =
        exercise?.notes ?? "";

    editor.querySelector(".remove-exercise")
        .addEventListener("click", () => {
            editor.remove();
            updateExerciseNumbers();
        });

    editor.querySelector(".add-set")
        .addEventListener(
            "click",
            () => addSetEditor(editor)
        );

    const sets = exercise
        ? exercise.sets
        : [null, null, null];

    for (const set of sets) {
        addSetEditor(editor, set);
    }

    exerciseEditors.append(editor);
    updateExerciseNumbers();
}

function addSetEditor(exerciseEditor, set = null) {
    const setEditors =
        exerciseEditor.querySelector(".set-editors");

    if (setEditors.children.length >= 20) {
        showWorkoutFormError(
            "An exercise can contain up to 20 sets."
        );
        return;
    }

    const setEditor = setEditorTemplate.content
        .firstElementChild
        .cloneNode(true);

    setEditor.querySelector(".set-weight").value =
        set?.weightKg ?? "";

    setEditor.querySelector(".set-reps").value =
        set?.reps ?? "";

    setEditor.querySelector(".remove-set")
        .addEventListener("click", () => {
            setEditor.remove();
            updateSetNumbers(exerciseEditor);
        });

    setEditors.append(setEditor);
    updateSetNumbers(exerciseEditor);
}

function updateExerciseNumbers() {
    [...exerciseEditors.children]
        .forEach((editor, index) => {
            editor.querySelector(
                ".exercise-editor-number"
            ).textContent = `Exercise ${index + 1}`;
        });
}

function updateSetNumbers(exerciseEditor) {
    [...exerciseEditor.querySelectorAll(".set-editor")]
        .forEach((editor, index) => {
            editor.querySelector(
                ".set-editor-number"
            ).textContent = `Set ${index + 1}`;
        });
}

function updateRestWorkoutState() {
    const resting = restWorkoutCheckbox.checked;
    const workoutName = workoutForm.elements.workout;

    exerciseEditorSection.hidden = resting;

    exerciseEditorSection
        .querySelectorAll("input, textarea, button")
        .forEach(element => {
            element.disabled = resting;
        });

    if (resting) {
        workoutName.value = "Rest";
        workoutName.readOnly = true;
    } else {
        if (!editingWorkoutName
                && workoutName.value === "Rest") {
            workoutName.value = "";
        }

        workoutName.readOnly = editingWorkoutName != null;
    }
}

async function saveWorkout(event) {
    event.preventDefault();
    clearWorkoutFormStatus();

    if (!workoutForm.reportValidity()) {
        return;
    }

    const resting = restWorkoutCheckbox.checked;
    const exercises = resting
        ? []
        : [...exerciseEditors.children]
            .map(createExercisePayload);

    if (!resting && exercises.length === 0) {
        showWorkoutFormError(
            "Add at least one exercise or select rest day."
        );
        return;
    }

    const payload = {
        date: workoutForm.elements.date.value,
        workout: resting
            ? "Rest"
            : workoutForm.elements.workout.value.trim(),
        notes: optionalText(
            workoutForm.elements.notes.value
        ),
        exercises
    };

    setWorkoutSaving(true);

    try {
        const response = await fetch("/api/workouts", {
            method: "POST",
            headers: mutationHeaders(),
            body: JSON.stringify(payload)
        });

        requireAuthenticated(response, true);

        if (!response.ok) {
            throw new Error(
                `Workout request failed: ${response.status}`
            );
        }

        const result = await response.json();

        workoutDialog.close();
        dateInput.value = payload.date;
        await loadDashboard();

        showSuccess(
            result.action === "updated"
                ? "Workout updated successfully."
                : "Workout saved successfully."
        );
    } catch (error) {
        console.error(error);
        showWorkoutFormError("Could not save workout.");
    } finally {
        setWorkoutSaving(false);
    }
}

function createExercisePayload(editor) {
    const sets = [...editor.querySelectorAll(".set-editor")]
        .map(setEditor => ({
            weightKg: optionalNumber(
                setEditor.querySelector(".set-weight").value
            ),
            reps: Number(
                setEditor.querySelector(".set-reps").value
            )
        }));

    return {
        name: editor.querySelector(".exercise-name")
            .value
            .trim(),
        sets,
        notes: optionalText(
            editor.querySelector(".exercise-notes").value
        )
    };
}

function setWorkoutSaving(saving) {
    saveWorkoutButton.disabled = saving;
    saveWorkoutButton.textContent = saving
        ? "Saving…"
        : "Save Workout";
}

function clearWorkoutFormStatus() {
    workoutFormStatus.textContent = "";
    workoutFormStatus.classList.remove("error");
}

function showWorkoutFormError(message) {
    workoutFormStatus.textContent = message;
    workoutFormStatus.classList.add("error");
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

async function logout() {
    if (!csrfToken) {
        redirectToLogin();
        return;
    }

    logoutButton.disabled = true;
    logoutButton.textContent = "Signing out…";

    try {
        const response = await fetch("/api/auth/logout", {
            method: "POST",
            headers: {
                [csrfHeaderName]: csrfToken
            }
        });

        if (!response.ok) {
            throw new Error(
                `Logout request failed: ${response.status}`
            );
        }

        redirectToLogin();
    } catch (error) {
        console.error(error);
        showError("Could not sign out. Please try again.");
        logoutButton.disabled = false;
        logoutButton.textContent = "Sign Out";
    }
}

function mutationHeaders() {
    return {
        [csrfHeaderName]: csrfToken,
        "Content-Type": "application/json"
    };
}

function requireAuthenticated(response, includeForbidden = false) {
    if (response.status === 401
            || (includeForbidden && response.status === 403)) {
        redirectToLogin();
        throw new Error("Authentication required.");
    }
}

function redirectToLogin() {
    window.location.replace("/login.html");
}
