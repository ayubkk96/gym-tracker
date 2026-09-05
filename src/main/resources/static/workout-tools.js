// Uses the existing workout editor. Templates never submit or log a workout.
const templateSelect = document.querySelector("#workout-template-select");
const templateStatus = document.querySelector("#workout-template-status");
const applyTemplateButton = document.querySelector("#apply-workout-template");
const deleteTemplateButton = document.querySelector("#delete-workout-template");
const saveTemplateButton = document.querySelector("#save-workout-template");
const previousStatus = document.querySelector("#previous-workout-status");
let workoutTemplates = [];
let previousWorkout = null;
let previousLookupId = 0;
let templateLoadId = 0;
let editorGeneration = 0;
let previousTimer = null;
let templateBusy = false;
let workoutWriting = false;
let templateEditingWorkout = false;

templateSelect.addEventListener("change", updateTemplateControls);
applyTemplateButton.addEventListener("click", applyWorkoutTemplate);
deleteTemplateButton.addEventListener("click", deleteWorkoutTemplate);
saveTemplateButton.addEventListener("click", saveWorkoutTemplate);
workoutForm.elements.workout.addEventListener("input", schedulePreviousWorkout);
workoutForm.elements.date.addEventListener("change", schedulePreviousWorkout);
exerciseEditors.addEventListener("input", renderPreviousSets);
workoutDialog.addEventListener("close", () => {
    editorGeneration++;
    previousLookupId++;
    templateLoadId++;
    clearTimeout(previousTimer);
});

function resetWorkoutTools(editing) {
    editorGeneration++;
    previousLookupId++;
    templateLoadId++;
    clearTimeout(previousTimer);
    previousWorkout = null;
    previousStatus.textContent = "";
    templateStatus.textContent = "";
    templateEditingWorkout = editing;
    document.querySelector("#workout-template-tools").hidden = editing;
}

function updateTemplateControls() {
    const selected = workoutTemplates.some(template => String(template.id) === templateSelect.value);
    const blocked = templateBusy || workoutWriting;
    templateSelect.disabled = blocked || templateEditingWorkout;
    applyTemplateButton.disabled = blocked || !selected || templateEditingWorkout;
    deleteTemplateButton.disabled = blocked || !selected || templateEditingWorkout;
    saveTemplateButton.disabled = blocked || restWorkoutCheckbox.checked;
}

function setWorkoutToolsSaving(saving) {
    workoutWriting = saving;
    updateTemplateControls();
}

async function fetchTemplates() {
    const response = await fetch("/api/workout-templates");
    requireAuthenticated(response);
    if (!response.ok) throw requestError(response, "Could not load templates.");
    return response.json();
}

async function loadWorkoutTemplates(selectId = "") {
    const loadId = ++templateLoadId;
    templateStatus.textContent = "Loading templates…";
    try {
        const templates = await fetchTemplates();
        if (loadId !== templateLoadId) return;
        workoutTemplates = templates;
        templateSelect.replaceChildren();
        const prompt = document.createElement("option");
        prompt.value = "";
        prompt.textContent = "Choose a template";
        templateSelect.append(prompt);
        for (const template of templates) {
            const option = document.createElement("option");
            option.value = String(template.id);
            option.textContent = template.name;
            templateSelect.append(option);
        }
        templateSelect.value = String(selectId);
        templateStatus.textContent = templates.length ? "Use a template to fill the exercise names and set counts." : "No templates yet. Add exercises below, then save your first template.";
    } catch (error) {
        if (loadId !== templateLoadId) return;
        workoutTemplates = [];
        templateStatus.textContent = "Could not load templates. Reopen this form to retry. You can still log a workout." + requestReference(error);
    } finally {
        if (loadId === templateLoadId) updateTemplateControls();
    }
}

function templateDraft(template) {
    return template.exercises.map(exercise => ({
        name: exercise.name, notes: exercise.notes,
        sets: Array.from({ length: exercise.setCount }, () => ({ weightKg: null, reps: null }))
    }));
}

function applyWorkoutTemplate() {
    const template = workoutTemplates.find(item => String(item.id) === templateSelect.value);
    if (!template || templateBusy || workoutWriting || templateEditingWorkout) return;
    const hasDraft = workoutForm.elements.workout.value.trim() || workoutForm.elements.notes.value.trim()
        || [...exerciseEditors.querySelectorAll("input, textarea")].some(input => input.value.trim());
    if (hasDraft && !window.confirm("Replace the exercise draft with this template? Unsaved weights and reps will be cleared.")) return;
    restWorkoutCheckbox.checked = false;
    workoutForm.elements.workout.value = template.name;
    workoutForm.elements.notes.value = template.notes ?? "";
    exerciseEditors.replaceChildren();
    for (const exercise of templateDraft(template)) addExerciseEditor(exercise);
    updateRestWorkoutState();
    clearWorkoutFormStatus();
    templateStatus.textContent = "Template applied. Enter today's weights and reps, then save the workout.";
}

async function saveWorkoutTemplate() {
    if (templateBusy || workoutWriting || restWorkoutCheckbox.checked) return;
    if (!workoutForm.elements.workout.reportValidity() || !workoutForm.elements.notes.reportValidity()) return;
    const editors = [...exerciseEditors.children];
    if (!editors.length) { showWorkoutFormError("Add at least one exercise to save a template."); return; }
    const exercises = [];
    for (const editor of editors) {
        const name = editor.querySelector(".exercise-name");
        const notes = editor.querySelector(".exercise-notes");
        const count = editor.querySelectorAll(".set-editor").length;
        if (!name.reportValidity() || !notes.reportValidity()) return;
        if (count < 1 || count > 20) { showWorkoutFormError("Each template exercise needs 1–20 sets."); return; }
        exercises.push({ name: name.value.trim(), notes: optionalText(notes.value), setCount: count });
    }
    const payload = { name: workoutForm.elements.workout.value.trim(), notes: optionalText(workoutForm.elements.notes.value), exercises };
    const generation = editorGeneration;
    templateBusy = true;
    updateTemplateControls();
    try {
        const latest = await fetchTemplates();
        if (generation !== editorGeneration) return;
        if (latest.some(item => normalizeExerciseName(item.name) === normalizeExerciseName(payload.name))
            && !window.confirm(`Replace the saved template “${payload.name}”? Your logged workouts will stay unchanged.`)) return;
        const response = await fetch("/api/workout-templates", {
            method: "POST", headers: mutationHeaders(), body: JSON.stringify(payload)
        });
        requireAuthenticated(response, true);
        if (!response.ok) throw requestError(response, "Template save failed.");
        const saved = await response.json();
        if (generation !== editorGeneration) return;
        await loadWorkoutTemplates(saved.id);
        if (generation !== editorGeneration) return;
        workoutFormStatus.textContent = "Template saved. This has not recorded a workout.";
        workoutFormStatus.classList.remove("error");
    } catch (error) {
        if (generation === editorGeneration) showWorkoutFormError("Could not save the template. Check its name and exercises, then try again." + requestReference(error));
    } finally {
        templateBusy = false;
        updateTemplateControls();
    }
}

async function deleteWorkoutTemplate() {
    const template = workoutTemplates.find(item => String(item.id) === templateSelect.value);
    if (!template || templateBusy || workoutWriting || !window.confirm(`Delete template “${template.name}”? Logged workouts will stay unchanged.`)) return;
    const generation = editorGeneration;
    templateBusy = true;
    updateTemplateControls();
    try {
        const response = await fetch(`/api/workout-templates/${encodeURIComponent(template.id)}`, {
            method: "DELETE", headers: mutationHeaders()
        });
        requireAuthenticated(response, true);
        if (!response.ok && response.status !== 404) throw requestError(response, "Template deletion failed.");
        if (generation === editorGeneration) await loadWorkoutTemplates();
    } catch (error) {
        if (generation === editorGeneration) templateStatus.textContent = "Could not delete the template. Please try again." + requestReference(error);
    } finally {
        templateBusy = false;
        updateTemplateControls();
    }
}

function schedulePreviousWorkout() {
    clearTimeout(previousTimer);
    previousLookupId++;
    previousWorkout = null;
    renderPreviousSets();
    const name = workoutForm.elements.workout.value.trim();
    const before = workoutForm.elements.date.value;
    if (restWorkoutCheckbox.checked || !name || !before) {
        previousStatus.textContent = restWorkoutCheckbox.checked ? "" : "Enter a workout name and date to see your previous session.";
        return;
    }
    previousStatus.textContent = "Looking for your previous session…";
    const lookupId = previousLookupId;
    previousTimer = setTimeout(() => loadPreviousWorkout(name, before, lookupId), 300);
}

async function loadPreviousWorkout(name, before, lookupId) {
    try {
        const response = await fetch(`/api/workouts/previous?${new URLSearchParams({ name, before })}`);
        requireAuthenticated(response);
        if (lookupId !== previousLookupId || !workoutDialog.open) return;
        if (response.status === 204) {
            previousStatus.textContent = "No earlier session with this workout name. This one will be your starting point.";
            return;
        }
        if (!response.ok) throw requestError(response, "Comparison unavailable.");
        const previous = await response.json();
        if (lookupId !== previousLookupId || !workoutDialog.open) return;
        previousWorkout = previous;
        previousStatus.textContent = `Comparing with ${formatHistoryDate(previous.date)} · ${previous.name}. Rep changes are shown only at the same weight.`;
        renderPreviousSets();
    } catch (error) {
        if (lookupId !== previousLookupId || !workoutDialog.open) return;
        previousStatus.textContent = "Could not load the previous session. You can still save this workout. Change the date or name to retry." + requestReference(error);
    }
}

function normalizeExerciseName(name) { return name.trim().toLowerCase(); }

function previousSetText(previous, weightInput, repsInput) {
    if (!previous) return "New set — no matching set last time.";
    const weight = previous.weightKg == null ? "BW" : `${previous.weightKg}kg`;
    const label = `Last: ${weight} × ${previous.reps}`;
    if (repsInput === "") return label;
    const currentWeight = weightInput === "" ? null : Number(weightInput);
    const reps = Number(repsInput);
    if (!Number.isInteger(reps) || reps < 1 || currentWeight !== previous.weightKg) return label;
    const change = reps - previous.reps;
    return `${label} · ${change > 0 ? "+" : ""}${change} reps`;
}

function renderPreviousSets() {
    const occurrences = new Map();
    for (const editor of exerciseEditors.children) {
        const key = normalizeExerciseName(editor.querySelector(".exercise-name").value);
        const occurrence = occurrences.get(key) || 0;
        occurrences.set(key, occurrence + 1);
        const previous = previousWorkout?.exercises.filter(exercise => normalizeExerciseName(exercise.name) === key)[occurrence];
        editor.querySelector(".previous-exercise").textContent = previousWorkout && !previous ? "This exercise wasn't in the previous session." : "";
        [...editor.querySelectorAll(".set-editor")].forEach((set, index) => {
            set.querySelector(".previous-set").textContent = previous ? previousSetText(previous.sets[index],
                set.querySelector(".set-weight").value, set.querySelector(".set-reps").value) : "";
        });
    }
}
