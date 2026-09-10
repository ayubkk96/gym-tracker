const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const root = path.join(__dirname, "../../main/resources/static");
function demo() {
    const nodes = new Map();
    function node() {
        return {
            children: [], dataset: {}, attributes: {}, listeners: {}, textContent: "",
            append(...items) { this.children.push(...items); },
            replaceChildren(...items) { this.children = items; },
            setAttribute(key, value) { this.attributes[key] = value; },
            removeAttribute(key) { delete this.attributes[key]; },
            addEventListener(key, action) { this.listeners[key] = action; }
        };
    }
    const html = fs.readFileSync(path.join(root, "demo.html"), "utf8");
    for (const match of html.matchAll(/id="([^"]+)"/g)) {
        assert.ok(!nodes.has("#" + match[1]), "unique ID");
        nodes.set("#" + match[1], node());
    }
    vm.runInNewContext(fs.readFileSync(path.join(root, "demo.js"), "utf8"), {
        document: { createElement: node, querySelector(selector) {
            assert.ok(nodes.has(selector), "selector exists: " + selector);
            return nodes.get(selector);
        } },
        fetch() { assert.fail("Demo must not call an API"); }
    });
    return nodes;
}
test("public demo renders sample data without APIs and navigates between rest and training", () => {
    const nodes = demo();
    assert.equal(nodes.get("#calories-value").textContent, "2,400 kcal");
    assert.equal(nodes.get("#demo-week").children.length, 6);
    assert.equal(nodes.get("#demo-history").children.length, 7);
    const rest = nodes.get("#demo-history").children[0];
    rest.listeners.click();
    assert.equal(rest.attributes["aria-current"], "date");
    assert.equal(nodes.get("#workout-container").children.length, 1);
    assert.equal(nodes.get("#workout-container").children[0].children[1].textContent, "Recovery day");
    const select = nodes.get("#demo-date");
    select.value = "2026-09-03";
    select.listeners.change();
    assert.equal(rest.attributes["aria-current"], undefined);
    const workout = nodes.get("#workout-container").children[0];
    assert.equal(workout.children.length, 3);
    assert.equal(workout.children[1].children[1].children[0].children[1].textContent, "BW × 8");
    assert.equal(nodes.get("#calories-value").textContent, "2,460 kcal");
});

