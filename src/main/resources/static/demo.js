"use strict";

// Fictional fixtures only: this page never reads or writes account data.
const demoDays = [
    { date: "2026-09-01", calories: 2340, protein: 170, carbs: 262, fat: 68, weight: 78.5, workout: "Chest + Triceps", exercises: [
        { name: "Bench Press", weight: 60, reps: [10, 10, 8] },
        { name: "Rope Pushdown", weight: 18, reps: [12, 12, 10] }
    ] },
    { date: "2026-09-02", calories: 2250, protein: 162, carbs: 252, fat: 66, weight: null, workout: "Rest", exercises: [] },
    { date: "2026-09-03", calories: 2460, protein: 182, carbs: 280, fat: 68, weight: 78.4, workout: "Back + Biceps", exercises: [
        { name: "Pull-ups", weight: null, reps: [8, 7, 6] },
        { name: "Seated Cable Row", weight: 45, reps: [12, 12, 10] }
    ] },
    { date: "2026-09-04", calories: 2380, protein: 174, carbs: 268, fat: 68, weight: null, workout: "Legs", exercises: [
        { name: "Squat", weight: 70, reps: [10, 10, 10] },
        { name: "Leg Extension", weight: 35, reps: [12, 12, 12] }
    ] },
    { date: "2026-09-05", calories: 2220, protein: 160, carbs: 242, fat: 68, weight: 78.3, workout: "Rest", exercises: [] },
    { date: "2026-09-06", calories: 2400, protein: 180, carbs: 267, fat: 68, weight: null, workout: "Chest + Triceps", exercises: [
        { name: "Bench Press", weight: 60, reps: [10, 10, 10] },
        { name: "Rope Pushdown", weight: 18, reps: [12, 12, 12] }
    ] },
    { date: "2026-09-07", calories: 2320, protein: 172, carbs: 255, fat: 68, weight: 78.2, workout: "Rest", exercises: [] }
];
const targets = { calories: 2400, protein: 180, carbs: 270, fat: 70 };
const dateSelect = document.querySelector("#demo-date");
const history = document.querySelector("#demo-history");
const formatDate = value => new Date(value + "T12:00:00Z").toLocaleDateString("en-GB", {
    weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC"
});
const number = value => value.toLocaleString("en-GB");

function element(tag, className, text) {
    const node = document.createElement(tag);
    node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function showDay(date) {
    const day = demoDays.find(item => item.date === date);
    if (!day) return;
    dateSelect.value = date;
    document.querySelector("#day-title").textContent = formatDate(date);
    document.querySelector("#demo-status").textContent = "Showing sample data for " + formatDate(date) + ".";
    document.querySelector("#demo-weight").textContent = day.weight === null ? "Weight: not recorded" : "Weight: " + day.weight + " kg";
    document.querySelector("#demo-notes").textContent = day.exercises.length ? "Training day · " + day.workout : "Recovery day. Time to recharge.";
    for (const [key, target] of Object.entries(targets)) {
        const unit = key === "calories" ? " kcal" : "g";
        document.querySelector("#" + key + "-value").textContent = number(day[key]) + unit;
        document.querySelector("#" + key + "-target").textContent = "Daily target " + number(target) + unit;
        const progress = document.querySelector("#" + key + "-progress");
        progress.max = target;
        progress.value = Math.min(day[key], target);
    }
    const card = element("article", "workout-card");
    const heading = element("div", "workout-title-row");
    heading.append(element("h3", "workout-title", day.workout));
    card.append(heading);
    if (!day.exercises.length) card.append(element("p", "workout-empty", "Recovery day"));
    for (const exercise of day.exercises) {
        const item = element("div", "exercise");
        const title = element("div", "exercise-heading");
        title.append(element("h4", "", exercise.name), element("span", "total-reps", exercise.reps.reduce((a, b) => a + b, 0) + " total reps"));
        const sets = element("div", "set-list");
        exercise.reps.forEach((reps, index) => {
            const set = element("div", "set");
            set.append(element("span", "set-label", "Set " + (index + 1)), element("span", "", (exercise.weight === null ? "BW" : exercise.weight + " kg") + " × " + reps));
            sets.append(set);
        });
        item.append(title, sets);
        card.append(item);
    }
    document.querySelector("#workout-container").replaceChildren(card);
    for (const button of history.children) {
        if (button.dataset.date === date) button.setAttribute("aria-current", "date");
        else button.removeAttribute("aria-current");
    }
}

for (const day of [...demoDays].reverse()) {
    const option = element("option", "", formatDate(day.date));
    option.value = day.date;
    dateSelect.append(option);
    const button = element("button", "history-row");
    button.type = "button";
    button.dataset.date = day.date;
    button.append(element("span", "history-date", formatDate(day.date)), element("strong", "history-details", day.workout), element("span", "history-meta", number(day.calories) + " kcal · " + day.protein + "g protein"));
    button.addEventListener("click", () => showDay(day.date));
    history.append(button);
}
const summary = Object.keys(targets).map(key => [
    "Average " + key, number(Math.round(demoDays.reduce((sum, day) => sum + day[key], 0) / demoDays.length)) + (key === "calories" ? " kcal" : "g")
]);
summary.push(["Training sessions", demoDays.filter(day => day.exercises.length).length], ["Nutrition recorded", demoDays.length + " of 7"]);
for (const [label, value] of summary) {
    const card = element("article", "summary-card");
    card.append(element("span", "", label), element("strong", "", value));
    document.querySelector("#demo-week").append(card);
}
dateSelect.addEventListener("change", () => showDay(dateSelect.value));
showDay("2026-09-06");

