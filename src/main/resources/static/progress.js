const progressPeriod = document.querySelector("#progress-period");
const progressExercise = document.querySelector("#progress-exercise");
const progressStatus = document.querySelector("#progress-status");
const progressCharts = document.querySelector("#progress-charts");
const progressRecords = document.querySelector("#progress-records");
let progressRequest = 0;
progressPeriod.addEventListener("change", loadProgress);
progressExercise.addEventListener("change", loadProgress);
document.querySelector("#progress-retry").addEventListener("click", loadProgress);

async function loadProgress() {
    const id = ++progressRequest;
    const through = dateInput.value;
    const exercise = progressExercise.value;
    progressCharts.replaceChildren();
    progressRecords.replaceChildren();
    progressStatus.textContent = "Loading progress…";
    if (!through) { progressStatus.textContent = "Choose a dashboard date to see progress."; return; }
    try {
        const query = new URLSearchParams({ through, days: progressPeriod.value, exercise });
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
        progressStatus.textContent = `${data.from} to ${data.through} · Dots are logged dates; gaps are not zero. Exercise names match ignoring case and surrounding spaces.`;
        progressCharts.append(progressChart("Bodyweight", data.bodyweight, "kg", data.from, data.through));
        if (exercise && progressExercise.value) {
            progressCharts.append(progressChart("Heaviest set · " + exercise, data.heaviestSets, "kg", data.from, data.through));
            progressCharts.append(progressChart("Best bodyweight set · " + exercise, data.bodyweightReps, "reps", data.from, data.through));
            progressRecords.append(recordCard("Heaviest set", data.heaviest), recordCard("Best bodyweight set", data.bestBodyweight));
        } else {
            const hint = document.createElement("p");
            hint.textContent = data.exercises.length ? "Choose an exercise to see its charts and records." : "Log your first workout to start building exercise records.";
            progressRecords.append(hint);
        }
    } catch (error) {
        if (id !== progressRequest || through !== dateInput.value) return;
        progressStatus.textContent = "Could not load progress. Select Refresh progress to retry. Your logs are unchanged." + requestReference(error);
    }
}

function plotPoints(points, from, through) {
    const values = points.map(point => Number(point.value));
    const min = Math.min(...values), max = Math.max(...values);
    const pad = Math.max((max - min) * 0.1, 1);
    const low = Math.max(0, min - pad), high = max + pad;
    const start = Date.parse(from), span = Math.max(1, Date.parse(through) - start);
    return { low, high, points: points.map(point => ({...point,
        x: 48 + (Date.parse(point.date) - start) / span * 424,
        y: 170 - (Number(point.value) - low) / (high - low) * 144
    })) };
}

function progressChart(title, points, unit, from, through) {
    const card = document.createElement("article"); card.className = "progress-card";
    const heading = document.createElement("h3"); heading.textContent = title; card.append(heading);
    if (!points.length) {
        const empty = document.createElement("p"); empty.textContent = "No measurements logged in this period."; card.append(empty); return card;
    }
    const geometry = plotPoints(points, from, through);
    const svg = svgNode("svg", {viewBox: "0 0 520 205", role: "img", "aria-label": `${title}, ${points.length} logged dates. Exact values in the table below.`});
    svg.append(svgNode("line", {x1:48,y1:170,x2:472,y2:170,stroke:"currentColor"}));
    for (const [value, y] of [[geometry.low,170],[geometry.high,26]]) {
        const label = svgNode("text", {x:44,y,"text-anchor":"end"}); label.textContent = value.toFixed(1); svg.append(label);
    }
    for (const [value,x,anchor] of [[from,48,"start"],[through,472,"end"]]) {
        const label = svgNode("text", {x,y:195,"text-anchor":anchor}); label.textContent=value; svg.append(label);
    }
    // Only actual measurements are plotted; no interpolation across missing dates.
    for (const point of geometry.points) {
        const dot = svgNode("circle", {cx:point.x,cy:point.y,r:4,fill:"currentColor"});
        const tooltip = svgNode("title", {}); tooltip.textContent = `${point.date}: ${point.value} ${unit}`; dot.append(tooltip); svg.append(dot);
    }
    card.append(svg);
    const details = document.createElement("details");
    const summary = document.createElement("summary"); summary.textContent = `View ${points.length} measurements (${unit})`; details.append(summary);
    const table = document.createElement("table");
    const caption = document.createElement("caption"); caption.textContent = `${title} — ${unit}`; table.append(caption);
    const header = document.createElement("tr");
    for (const text of ["Date",unit]) { const th=document.createElement("th"); th.textContent=text; th.scope="col"; header.append(th); }
    table.append(header);
    for (const point of points) {
        const row=document.createElement("tr");
        for (const text of [point.date,String(point.value)]) { const td=document.createElement("td"); td.textContent=text; row.append(td); }
        table.append(row);
    }
    details.append(table); card.append(details); return card;
}

function svgNode(name, attributes) {
    const node = document.createElementNS("http://www.w3.org/2000/svg", name);
    for (const [key,value] of Object.entries(attributes)) node.setAttribute(key,String(value));
    return node;
}

function recordCard(title, record) {
    const card=document.createElement("article"); card.className="progress-card";
    const heading=document.createElement("h3"); heading.textContent=title;
    const value=document.createElement("p"); value.className="record-value";
    value.textContent=record ? `${record.weightKg == null ? "BW" : record.weightKg + "kg"} × ${record.reps}` : "No record yet";
    const date=document.createElement("p"); date.textContent=record ? record.date : "Records appear when you log a matching set.";
    card.append(heading,value,date); return card;
}
