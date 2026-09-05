// Dependency-free unit/markup-contract tests; these do not launch a browser.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../main/resources/static');

function element() {
    const attributes = {};
    return {
        value: '', textContent: '', disabled: false, hidden: false,
        children: [], attributes,
        classList: { add() {}, remove() {} },
        addEventListener() {}, querySelectorAll() { return []; },
        setAttribute(name, value) { attributes[name] = value; },
        append(...children) { this.children.push(...children); },
        replaceChildren(...children) { this.children = children; },
        focus() {}, dataset: {},
        elements: { startDate: { value: '' } }
    };
}

function harness(script = 'app.js') {
    const nodes = new Map();
    const get = selector => {
        if (!nodes.has(selector)) nodes.set(selector, element());
        return nodes.get(selector);
    };
    const context = vm.createContext({
        document: { querySelector: get, querySelectorAll: () => [], createElement: element },
        window: { location: { replace() {} } },
        console: { error() {} }, Intl, Date,
        fetch: async () => { throw Error('Unexpected network request'); }
    });
    const source = fs.readFileSync(path.join(root, script), 'utf8')
        .replace(/^initialiseDashboard\(\);$/m, '')
        .replace(/^initialiseAuthentication\(\);$/m, '');
    vm.runInContext(source, context);
    return { get, context, run: source => vm.runInContext(source, context) };
}

test('both pages have unique IDs and every literal JS ID selector exists', () => {
    for (const [page, script] of [['index.html', 'app.js'], ['login.html', 'auth.js'], ['reset-password.html', 'reset-password.js']]) {
        const html = fs.readFileSync(path.join(root, page), 'utf8');
        const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
        assert.equal(ids.length, new Set(ids).size, `${page} duplicate ID`);
        const source = fs.readFileSync(path.join(root, script), 'utf8');
        for (const match of source.matchAll(/querySelector\(\s*"#([\w-]+)"\s*\)/g)) {
            assert.ok(ids.includes(match[1]), `${page}: missing ${match[1]}`);
        }
        for (const match of html.matchAll(/(?:aria-labelledby|aria-controls|href)="#?([\w-]+)"/g)) {
            assert.ok(ids.includes(match[1]), `${page}: broken reference ${match[1]}`);
        }
    }
});

test('missing nutrition differs from an actual zero, including progress label', () => {
    const h = harness();
    h.run('updateMacro("protein", null, 180, "g")');
    assert.equal(h.get('#protein-value').textContent, '—');
    assert.equal(h.get('#protein-progress').attributes['aria-valuetext'], 'Not logged');
    h.run('updateMacro("protein", 0, 180, "g")');
    assert.equal(h.get('#protein-value').textContent, '0g');
    assert.equal(h.get('#protein-target').textContent, 'Daily target 180g');
});

test('large calorie values are grouped and a zero target remains valid progress', () => {
    const h = harness();
    h.run('updateMacro("calories", 2450, 0, "")');
    assert.equal(h.get('#calories-value').textContent, '2,450');
    assert.equal(h.get('#calories-progress').max, 1);
});

test('date arrows cross month and year boundaries using local dates', () => {
    const h = harness();
    h.run('loadDashboard = async () => {}');
    h.get('#dashboard-date').value = '2026-12-31';
    h.run('moveDate(1)');
    assert.equal(h.get('#dashboard-date').value, '2027-01-01');
    h.get('#dashboard-date').value = '2026-03-01';
    h.run('moveDate(-1)');
    assert.equal(h.get('#dashboard-date').value, '2026-02-28');
});

test('editing is disabled while loading or while another date is still displayed', () => {
    const h = harness();
    h.get('#dashboard-date').value = '2026-09-05';
    h.run('currentDashboard = {date: "2026-09-04"}; setLoading(false)');
    assert.equal(h.get('#open-workout-form').disabled, true);
    h.run('currentDashboard.date = "2026-09-05"; setLoading(false)');
    assert.equal(h.get('#open-workout-form').disabled, false);
    h.run('setLoading(true)');
    assert.equal(h.get('#open-nutrition-form').disabled, true);
    assert.equal(h.get('#dashboard-content').attributes['aria-busy'], 'true');
});

test('an older dashboard response cannot overwrite a newer selection', async () => {
    const h = harness();
    const pending = [];
    h.context.fetch = () => new Promise(resolve => pending.push(resolve));
    h.run('renderDashboard = data => { currentDashboard = data; }');
    h.get('#dashboard-date').value = '2026-09-04';
    const first = h.run('loadDashboard()');
    h.get('#dashboard-date').value = '2026-09-05';
    const second = h.run('loadDashboard()');
    const response = date => ({ ok: true, status: 200, json: async () => ({ date }) });
    pending[1](response('2026-09-05'));
    await second;
    pending[0](response('2026-09-04'));
    await first;
    assert.equal(h.run('currentDashboard.date'), '2026-09-05');
    assert.equal(h.get('#open-workout-form').disabled, false);
});

test('a failed date request explains the retained snapshot and keeps edits disabled', async () => {
    const h = harness();
    h.run('currentDashboard = {date: "2026-09-04"}');
    h.get('#dashboard-date').value = '2026-09-05';
    h.context.fetch = async () => ({ ok: false, status: 500 });
    await h.run('loadDashboard()');
    assert.match(h.get('#status-message').textContent, /previous snapshot/);
    assert.equal(h.get('#open-nutrition-form').disabled, true);
    assert.equal(h.get('#load-dashboard').disabled, false);
});

test('account switch exposes exactly one form and updates accessible button state', () => {
    const h = harness('auth.js');
    h.run('showAuthPanel(true)');
    assert.equal(h.get('#sign-in-panel').hidden, true);
    assert.equal(h.get('#registration-panel').hidden, false);
    assert.equal(h.get('#show-registration').attributes['aria-pressed'], 'true');
    h.run('showAuthPanel(false)');
    assert.equal(h.get('#sign-in-panel').hidden, false);
    assert.equal(h.get('#registration-panel').hidden, true);
});
