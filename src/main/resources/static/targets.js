const targetsDialog = document.querySelector("#targets-dialog");
const targetsForm = document.querySelector("#targets-form");
const targetsStatus = document.querySelector("#targets-status");
const targetsSave = document.querySelector("#save-targets");
const targetsClose = document.querySelector("#close-targets");
let targetsSaving = false;

document.querySelector("#open-targets").addEventListener("click", () => {
    if (!currentDashboard || currentDashboard.date !== dateInput.value) return;
    targetsForm.reset();
    targetsForm.elements.effectiveFrom.value = currentDashboard.date;
    for (const key of ["calories", "proteinG", "carbsG", "fatG"]) {
        targetsForm.elements[key].value = currentDashboard.targets?.[key] ?? "";
    }
    targetsStatus.textContent = "";
    targetsDialog.showModal();
});
targetsClose.addEventListener("click", () => { if (!targetsSaving) targetsDialog.close(); });
targetsDialog.addEventListener("cancel", event => { if (targetsSaving) event.preventDefault(); });
targetsForm.addEventListener("submit", async event => {
    event.preventDefault();
    if (targetsSaving || !targetsForm.reportValidity()) return;
    const body = { effectiveFrom: targetsForm.elements.effectiveFrom.value };
    for (const key of ["calories", "proteinG", "carbsG", "fatG"]) body[key] = Number(targetsForm.elements[key].value);
    targetsSaving = true;
    for (const field of targetsForm.elements) field.disabled = true;
    targetsClose.disabled = true;
    targetsStatus.textContent = "Saving targets…";
    try {
        const response = await fetch("/api/targets", { method: "PUT", headers: mutationHeaders(), body: JSON.stringify(body) });
        requireAuthenticated(response);
        if (!response.ok) throw requestError(response, "Targets could not be saved");
        targetsDialog.close();
        await loadDashboard();
    } catch (error) {
        targetsStatus.textContent = "Could not save your targets. Check the values and try again." + requestReference(error);
    } finally {
        targetsSaving = false;
        for (const field of targetsForm.elements) field.disabled = false;
        targetsClose.disabled = false;
    }
});
