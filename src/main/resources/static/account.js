const accountDialog = document.querySelector("#account-dialog");
const accountStatus = document.querySelector("#account-status");
const deleteAccountForm = document.querySelector("#delete-account-form");
const exportAccountButton = document.querySelector("#export-account");
const closeAccountButton = document.querySelector("#close-account");
let accountBusy = false;
document.querySelector("#open-account").addEventListener("click", () => {
    deleteAccountForm.reset(); accountStatus.textContent = ""; accountDialog.showModal();
});
closeAccountButton.addEventListener("click", () => { if (!accountBusy) accountDialog.close(); });
accountDialog.addEventListener("cancel", event => { if (accountBusy) event.preventDefault(); });
accountDialog.addEventListener("close", () => deleteAccountForm.reset());
exportAccountButton.addEventListener("click", exportAccount);
deleteAccountForm.addEventListener("submit", deleteAccount);

function setAccountBusy(busy) {
    accountBusy = busy;
    exportAccountButton.disabled = busy;
    closeAccountButton.disabled = busy;
    for (const input of deleteAccountForm.elements) input.disabled = busy;
}

async function exportAccount() {
    if (accountBusy) return;
    setAccountBusy(true); accountStatus.textContent = "Preparing your export…";
    try {
        const response = await fetch("/api/account/export", {cache:"no-store"});
        requireAuthenticated(response);
        if (!response.ok) throw requestError(response,"Export failed");
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url; link.download = "gym-tracker-export.json";
        document.body.append(link); link.click(); link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
        accountStatus.textContent = "Download started. Keep the file somewhere private; it contains your personal data.";
    } catch (error) {
        accountStatus.textContent = "Could not export your data. Please try again." + requestReference(error);
    } finally { setAccountBusy(false); }
}

async function deleteAccount(event) {
    event.preventDefault();
    if (accountBusy || !deleteAccountForm.reportValidity()) return;
    const body = {password:deleteAccountForm.elements.password.value, confirmation:deleteAccountForm.elements.confirmation.value};
    setAccountBusy(true); accountStatus.textContent = "Deleting your account…";
    try {
        const response = await fetch("/api/account", {method:"DELETE",headers:mutationHeaders(),body:JSON.stringify(body)});
        requireAuthenticated(response);
        if (response.status === 403) { accountStatus.textContent = "Deletion was not authorised. Check your password, or reload the page to refresh your session, then try again."; return; }
        if (response.status === 429) { accountStatus.textContent = "Too many deletion attempts. Wait 15 minutes before trying again."; return; }
        if (!response.ok) throw requestError(response,"Deletion failed");
        window.location.replace("/login.html");
    } catch (error) {
        accountStatus.textContent = "Could not confirm deletion. Sign in again to check your account before retrying." + requestReference(error);
    } finally {
        deleteAccountForm.elements.password.value = "";
        setAccountBusy(false);
    }
}
