const verificationStatus = document.querySelector("#verification-status");
const resendForm = document.querySelector("#resend-form");
const resendButton = document.querySelector("#resend-button");
const resendStatus = document.querySelector("#resend-status");

let csrfToken = null;
let csrfHeaderName = "X-CSRF-TOKEN";

const fragment = new URLSearchParams(window.location.hash.slice(1));
const token = fragment.get("token");
if (window.location.hash) {
    history.replaceState(null, "", window.location.pathname);
}

resendForm.querySelectorAll("input, button").forEach(element => {
    element.disabled = true;
});
resendForm.addEventListener("submit", resendVerification);

initialise();

async function initialise() {
    try {
        const response = await fetch("/api/auth/session");
        if (!response.ok) {
            throw new Error(`Session request failed: ${response.status}`);
        }
        const session = await response.json();
        csrfToken = session.csrfToken;
        csrfHeaderName = session.csrfHeaderName;
        resendForm.querySelectorAll("input, button").forEach(element => {
            element.disabled = false;
        });

        if (!token) {
            showStatus(
                verificationStatus,
                "Open the verification link from your email, or request a new one below.",
                "error"
            );
            return;
        }

        await confirmVerification(token);
    } catch (error) {
        console.error(error);
        showStatus(
            verificationStatus,
            "Could not connect to Gym Tracker. Please try again.",
            "error"
        );
    }
}

async function confirmVerification(rawToken) {
    const response = await fetch("/api/auth/email-verification/confirm", {
        method: "POST",
        headers: mutationHeaders(),
        body: JSON.stringify({ token: rawToken })
    });
    const body = await response.json().catch(() => ({}));

    if (response.ok) {
        showStatus(
            verificationStatus,
            body.message || "Email verified. You can now sign in.",
            "success"
        );
        return;
    }

    if (response.status === 429) {
        showStatus(
            verificationStatus,
            "Too many verification attempts. Please wait 15 minutes and try again.",
            "error"
        );
        return;
    }

    showStatus(
        verificationStatus,
        body.message || "This verification link is invalid or expired. Request a new one below.",
        "error"
    );
}

async function resendVerification(event) {
    event.preventDefault();
    if (!resendForm.reportValidity() || !csrfToken) return;

    const email = resendForm.elements.email.value.trim();
    setButtonLoading(true);
    clearStatus(resendStatus);

    try {
        const response = await fetch("/api/auth/email-verification/request", {
            method: "POST",
            headers: mutationHeaders(),
            body: JSON.stringify({ email })
        });
        const body = await response.json().catch(() => ({}));

        if (response.status === 429) {
            showStatus(
                resendStatus,
                "Too many requests. Please wait 15 minutes and try again.",
                "error"
            );
            return;
        }
        if (!response.ok) {
            showStatus(
                resendStatus,
                body.message || "Could not send a verification email. Please try again later.",
                "error"
            );
            return;
        }

        showStatus(
            resendStatus,
            body.message || "If that account needs verification, a new link will be sent.",
            "success"
        );
    } catch (error) {
        console.error(error);
        showStatus(
            resendStatus,
            "Could not send a verification email. Please try again later.",
            "error"
        );
    } finally {
        setButtonLoading(false);
    }
}

function mutationHeaders() {
    return {
        [csrfHeaderName]: csrfToken,
        "Content-Type": "application/json"
    };
}

function setButtonLoading(loading) {
    resendButton.disabled = loading;
    resendButton.textContent = loading
        ? "Sending…"
        : "Resend verification email";
}

function clearStatus(element) {
    element.textContent = "";
    element.classList.remove("error", "success");
}

function showStatus(element, message, type) {
    element.textContent = message;
    element.classList.remove("error", "success");
    element.classList.add(type);
}
