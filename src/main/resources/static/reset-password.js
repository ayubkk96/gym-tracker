const requestForm = document.querySelector("#recovery-request-form");
const confirmForm = document.querySelector("#recovery-confirm-form");
const recoveryStatus = document.querySelector("#recovery-status");
let recoveryCsrf = null;
let recoveryCsrfHeader = "X-CSRF-TOKEN";
// Fragments are not sent to the server or in Referer headers. Keep the token
// only in this page's memory, and immediately remove it from browser history.
let resetToken = new URLSearchParams(window.location.hash.slice(1)).get("token");
if (window.location.hash) window.history.replaceState(null, "", window.location.pathname);
if (resetToken) {
    requestForm.hidden = true;
    confirmForm.hidden = false;
    document.querySelector("#recovery-description").textContent = "Choose a new password. Your link expires after 15 minutes and works once.";
}

requestForm.addEventListener("submit", event => submitRecovery(event, false));
confirmForm.addEventListener("submit", event => submitRecovery(event, true));
initialiseRecovery();

async function initialiseRecovery() {
    try {
        const response = await fetch("/api/auth/session");
        if (!response.ok) throw new Error("Connection failed");
        const session = await response.json();
        recoveryCsrf = session.csrfToken;
        recoveryCsrfHeader = session.csrfHeaderName;
        document.querySelectorAll("form button").forEach(button => { button.disabled = false; });
    } catch {
        showRecoveryStatus("Could not connect. Refresh this page or request a new link.", true);
    }
}

async function submitRecovery(event, confirming) {
    event.preventDefault();
    const form = confirming ? confirmForm : requestForm;
    if (!form.reportValidity() || !recoveryCsrf) return;
    if (confirming && form.elements.password.value !== form.elements.confirmPassword.value) {
        showRecoveryStatus("Passwords do not match.", true);
        return;
    }
    const button = form.querySelector("button");
    const label = button.textContent;
    button.disabled = true;
    button.textContent = "Please wait…";
    try {
        const payload = confirming
            ? { token: resetToken, password: form.elements.password.value }
            : { email: form.elements.email.value.trim() };
        const response = await fetch(`/api/auth/password-reset/${confirming ? "confirm" : "request"}`, {
            method: "POST",
            headers: { "Content-Type": "application/json", [recoveryCsrfHeader]: recoveryCsrf },
            body: JSON.stringify(payload)
        });
        const data = await response.json().catch(() => ({}));
        const message = response.status === 429 ? "Too many attempts. Please try again later."
            : response.status === 403 ? "Your session expired. Refresh the page or request a new link."
            : data.message || "The request could not be completed. Please try again.";
        showRecoveryStatus(message, !response.ok);
        if (response.ok && confirming) {
            resetToken = null;
            form.reset();
            form.hidden = true;
            document.querySelector("#recovery-description").textContent = "Your password has been updated. Return to sign in.";
        }
    } catch {
        showRecoveryStatus("Could not connect. Please try again.", true);
    } finally {
        button.disabled = false;
        button.textContent = label;
    }
}

function showRecoveryStatus(message, error) {
    recoveryStatus.textContent = message;
    recoveryStatus.classList.toggle("error", error);
    recoveryStatus.classList.toggle("success", !error);
}
