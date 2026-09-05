const progressExercise = document.querySelector("#progress-exercise");
const progressStatus = document.querySelector("#progress-status");
const progressRecords = document.querySelector("#progress-records");
let progressRequest = 0;
progressExercise.addEventListener("change", loadProgress);
document.querySelector("#progress-retry").addEventListener("click", loadProgress);

async function loadProgress() {
    const id = ++progressRequest;
    const through = dateInput.value;
    const exercise = progressExercise.value;
    progressRecords.replaceChildren();
    progressStatus.textContent = "Loading progress…";
    if (!through) { progressStatus.textContent = "Choose a dashboard date to see progress."; return; }
    try {
        const query = new URLSearchParams({ through, exercise });
        const response = await fetch(`/api/progress?${query}`);
        requireAuthenticated(response);
        if (!response.ok) throw requestError(response, "Progress unavailable.");
        const data = await response.json();
        if (id !== progressRequest || through !== dateInput.value) return;
        progressExercise.replaceChildren();
        const placeholder = document.createElement("option");
        placeholder.value = ""; placeholder.textContent = "Choose an exercise";
        progressExercise.append(placeholder);
        for (const name of data.exercises) {
            const option = document.createElement("option");
            option.value = name; option.textContent = name;
            progressExercise.append(option);
        }
        progressExercise.value = data.exercises.includes(exercise) ? exercise : "";
        progressStatus.textContent = `Records through ${data.through}. Exercise names match ignoring case and surrounding spaces.`;
        if (exercise && progressExercise.value) {
            progressRecords.append(recordCard("Heaviest set", data.heaviest), recordCard("Best bodyweight set", data.bestBodyweight));
        } else {
            const hint = document.createElement("p");
            hint.textContent = data.exercises.length ? "Choose an exercise to see its records." : "Log your first workout to start building exercise records.";
            progressRecords.append(hint);
        }
    } catch (error) {
        if (id !== progressRequest || through !== dateInput.value) return;
        progressStatus.textContent = "Could not load progress. Select Refresh progress to retry. Your logs are unchanged." + requestReference(error);
    }
}

function recordCard(title, record) {
    const card=document.createElement("article"); card.className="progress-card";
    const heading=document.createElement("h3"); heading.textContent=title;
    const value=document.createElement("p"); value.className="record-value";
    value.textContent=record ? `${record.weightKg == null ? "BW" : record.weightKg + "kg"} × ${record.reps}` : "No record yet";
    const date=document.createElement("p"); date.textContent=record ? record.date : "Records appear when you log a matching set.";
    card.append(heading,value,date); return card;
}
