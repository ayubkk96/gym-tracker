const loginForm = document.querySelector("#login-form");
const registrationForm =
    document.querySelector("#registration-form");
const signInButton =
    document.querySelector("#sign-in-button");
const registerButton =
    document.querySelector("#register-button");
const pageStatus =
    document.querySelector("#auth-page-status");
const loginStatus =
    document.querySelector("#login-status");
const registrationStatus =
    document.querySelector("#registration-status");

let csrfToken = null;
let csrfHeaderName = "X-CSRF-TOKEN";

registrationForm.elements.startDate.value = getLocalDate();
setFormsDisabled(true);

loginForm.addEventListener("submit", signIn);
registrationForm.addEventListener("submit", register);
document.querySelector("#show-sign-in").addEventListener("click", () => showAuthPanel(false));
document.querySelector("#show-registration").addEventListener("click", () => showAuthPanel(true));

function showAuthPanel(registering) {
    document.querySelector("#sign-in-panel").hidden = registering;
    document.querySelector("#registration-panel").hidden = !registering;
    document.querySelector("#show-sign-in").setAttribute("aria-pressed", String(!registering));
    document.querySelector("#show-registration").setAttribute("aria-pressed", String(registering));
}

initialiseAuthentication();

async function initialiseAuthentication() {
    try {
        const session = await loadSession();

        if (session.authenticated) {
            window.location.replace("/");
            return;
        }

        csrfToken = session.csrfToken;
        csrfHeaderName = session.csrfHeaderName;
        setFormsDisabled(false);
        pageStatus.textContent = "";
    } catch (error) {
        console.error(error);
        pageStatus.textContent =
            "Could not connect to Gym Tracker. Please try again.";
        pageStatus.classList.add("error");
    }
}

async function loadSession() {
    const response = await fetch("/api/auth/session");

    if (!response.ok) {
        throw new Error(
            `Session request failed: ${response.status}`
        );
    }

    return response.json();
}

async function signIn(event) {
    event.preventDefault();

    if (!loginForm.reportValidity() || !csrfToken) {
        return;
    }

    const email = loginForm.elements.email.value.trim();
    const password = loginForm.elements.password.value;

    setButtonLoading(signInButton, true, "Signing in…");
    clearStatus(loginStatus);

    try {
        const response = await submitLogin(email, password);

        if (!response.ok) {
            throw new Error("Invalid email or password.");
        }

        window.location.replace("/");
    } catch (error) {
        console.error(error);
        showStatus(
            loginStatus,
            "Invalid email or password.",
            "error"
        );
    } finally {
        setButtonLoading(signInButton, false, "Sign In");
    }
}

async function register(event) {
    event.preventDefault();

    if (!registrationForm.reportValidity() || !csrfToken) {
        return;
    }

    const fields = registrationForm.elements;

    if (fields.password.value
            !== fields.confirmPassword.value) {
        showStatus(
            registrationStatus,
            "Passwords do not match.",
            "error"
        );
        return;
    }

    const payload = {
        email: fields.email.value.trim(),
        displayName: fields.displayName.value.trim(),
        password: fields.password.value,
        startDate: fields.startDate.value,
        targets: {
            calories: Number(fields.calories.value),
            proteinG: Number(fields.proteinG.value),
            carbsG: Number(fields.carbsG.value),
            fatG: Number(fields.fatG.value)
        }
    };

    setButtonLoading(
        registerButton,
        true,
        "Creating account…"
    );
    clearStatus(registrationStatus);

    try {
        const response = await fetch("/api/users", {
            method: "POST",
            headers: mutationHeaders(),
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            showStatus(
                registrationStatus,
                await registrationError(response),
                "error"
            );
            return;
        }

        const loginResponse = await submitLogin(
            payload.email,
            payload.password
        );

        if (!loginResponse.ok) {
            throw new Error(
                "Account created, but automatic sign-in failed."
            );
        }

        window.location.replace("/");
    } catch (error) {
        console.error(error);
        showStatus(
            registrationStatus,
            error.message || "Could not create your account.",
            "error"
        );
    } finally {
        setButtonLoading(
            registerButton,
            false,
            "Create Account"
        );
    }
}

function submitLogin(email, password) {
    const body = new URLSearchParams({
        username: email,
        password
    });

    return fetch("/api/auth/login", {
        method: "POST",
        headers: {
            [csrfHeaderName]: csrfToken,
            "Content-Type": "application/x-www-form-urlencoded"
        },
        body
    });
}

function mutationHeaders() {
    return {
        [csrfHeaderName]: csrfToken,
        "Content-Type": "application/json"
    };
}

async function registrationError(response) {
    if (response.status === 409) {
        return "An account already exists for that email.";
    }

    if (response.status === 400) {
        return "Check the account details and try again.";
    }

    return "Could not create your account.";
}

function setFormsDisabled(disabled) {
    loginForm
        .querySelectorAll("input, button")
        .forEach(element => {
            element.disabled = disabled;
        });

    registrationForm
        .querySelectorAll("input, button")
        .forEach(element => {
            element.disabled = disabled;
        });
}

function setButtonLoading(button, loading, text) {
    button.disabled = loading;
    button.textContent = text;
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

function getLocalDate() {
    const now = new Date();
    const offset = now.getTimezoneOffset() * 60_000;

    return new Date(now.getTime() - offset)
        .toISOString()
        .slice(0, 10);
}
