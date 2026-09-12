const {test} = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
function harness() {
    const nodes = new Map();
    const html = fs.readFileSync(path.join(__dirname, "../../main/resources/static/dashboard.html"), "utf8");
    const get = id => {
        assert.ok(html.includes('id="' + id.slice(1) + '"'));
        if (!nodes.has(id)) nodes.set(id, {listeners: {}, addEventListener(k, f) {this.listeners[k] = f;}});
        return nodes.get(id);
    };
    const fields = [];
    for (const name of ["effectiveFrom", "calories", "proteinG", "carbsG", "fatG"]) {
        const field = {value: "", disabled: false}; fields.push(field); fields[name] = field;
    }
    Object.assign(get("#targets-form"), {elements: fields, reset() {}, reportValidity: () => true});
    Object.assign(get("#targets-dialog"), {open: false, showModal() {this.open = true;}, close() {this.open = false;}});
    const ctx = {
        document: {querySelector: get}, currentDashboard: {date: "2026-09-12", targets: {calories: 2400, proteinG: 180, carbsG: 250, fatG: 70}},
        dateInput: {value: "2026-09-12"}, mutationHeaders: () => ({"X-CSRF-TOKEN": "token"}),
        requireAuthenticated() {}, requestError: () => Error("failed"), requestReference: () => "",
        fetch: async () => {throw Error("unexpected request");}, loadDashboard: async () => {}
    };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, "../../main/resources/static/targets.js"), "utf8"), ctx);
    return {ctx, get, fields};
}
test("targets prefill, save with CSRF, prevent duplicate submission and refresh", async () => {
    const h = harness();
    h.get("#open-targets").listeners.click();
    assert.equal(h.fields.calories.value, 2400);
    assert.equal(h.fields.effectiveFrom.value, "2026-09-12");
    h.fields.calories.value = "2200";
    let finish, calls = 0, refreshed = 0;
    h.ctx.fetch = (url, options) => {
        calls++;
        assert.equal(url, "/api/targets");
        assert.equal(options.headers["X-CSRF-TOKEN"], "token");
        assert.equal(JSON.parse(options.body).calories, 2200);
        return new Promise(resolve => finish = resolve);
    };
    h.ctx.loadDashboard = async () => refreshed++;
    const submit = () => h.get("#targets-form").listeners.submit({preventDefault() {}});
    const pending = submit();
    await submit();
    assert.equal(calls, 1);
    assert.equal(h.fields.calories.disabled, true);
    finish({ok: true});
    await pending;
    assert.equal(refreshed, 1);
    assert.equal(h.get("#targets-dialog").open, false);
    assert.equal(h.fields.calories.disabled, false);
});
test("failed target save keeps values and dialog available for retry", async () => {
    const h = harness();
    h.get("#open-targets").listeners.click();
    h.fields.calories.value = "2100";
    h.ctx.fetch = async () => ({ok: false, status: 500});
    await h.get("#targets-form").listeners.submit({preventDefault() {}});
    assert.equal(h.get("#targets-dialog").open, true);
    assert.equal(h.fields.calories.value, "2100");
    assert.match(h.get("#targets-status").textContent, /Could not save/);
    assert.equal(h.fields.calories.disabled, false);
});
