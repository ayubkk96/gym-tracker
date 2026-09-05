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
    for (const [page, script] of [['index.html', 'app.js'], ['index.html', 'account.js'], ['index.html', 'progress.js'], ['index.html', 'workout-tools.js'], ['login.html', 'auth.js'], ['reset-password.html', 'reset-password.js']]) {
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

function workoutToolsHarness() {
    const h = harness();
    h.get('#workout-form').elements.workout = element();
    h.get('#workout-form').elements.date = element();
    h.context.URLSearchParams = URLSearchParams;
    h.context.clearTimeout = () => {};
    h.run(fs.readFileSync(path.join(root, 'workout-tools.js'), 'utf8'));
    return h;
}

test('templates fill set counts without copying weights or reps', () => {
    const h = workoutToolsHarness();
    const draft = h.run('templateDraft({exercises:[{name:"Bench",setCount:6,notes:"Pause"}]})');
    assert.equal(draft[0].sets.length, 6);
    assert.equal(draft[0].name, 'Bench');
    assert.ok(draft[0].sets.every(set => set.weightKg === null && set.reps === null));
});

test('repeat copies every set independently, preserving bodyweight and zero weight', () => {
    const h = workoutToolsHarness();
    h.run('previousWorkout = {date:"2026-09-01",name:"Back",exercises:[{name:"Pull-ups",notes:"Pause",sets:Array.from({length:20},(_,i)=>({weightKg:i===0?null:0,reps:i+1}))}]}');
    const draft = h.run('repeatWorkoutDraft(previousWorkout)');
    assert.equal(draft[0].sets.length,20);
    assert.equal(draft[0].sets[0].weightKg,null);
    assert.equal(draft[0].sets[1].weightKg,0);
    assert.equal(draft[0].notes,'Pause');
    draft[0].sets[0].reps=99;
    assert.equal(h.run('previousWorkout.exercises[0].sets[0].reps'),1);
});

test('repeat requires matching earlier history and is disabled for edits, rest or saves', () => {
    const h = workoutToolsHarness();
    h.run('workoutForm.elements.workout.value=" back "; workoutForm.elements.date.value="2026-09-05"; previousWorkout={date:"2026-09-01",name:"Back",exercises:[{}]}');
    assert.equal(h.run('canRepeatWorkout()'),true);
    for (const flag of ['templateEditingWorkout','templateBusy','workoutWriting','restWorkoutCheckbox.checked']) {
        h.run(`${flag}=true`);
        assert.equal(h.run('canRepeatWorkout()'),false);
        h.run(`${flag}=false`);
    }
    h.run('previousWorkout.date="2026-09-05"');
    assert.equal(h.run('canRepeatWorkout()'),false);
    h.run('previousWorkout.date="2026-09-01"; workoutForm.elements.workout.value="Chest"');
    assert.equal(h.run('canRepeatWorkout()'),false);
    h.run('previousWorkout=null; updateTemplateControls()');
    assert.equal(h.get('#repeat-last-workout').disabled,true);
});

test('repeat confirms replacement and changes only the draft without saving', () => {
    const h = workoutToolsHarness();
    h.run('workoutForm.elements.workout.value="Back"; workoutForm.elements.date.value="2026-09-05"; workoutForm.elements.notes={value:"Today notes"}; previousWorkout={date:"2026-09-01",name:"Back",exercises:[{name:"Pull-ups",notes:null,sets:[{weightKg:null,reps:10}]}]}');
    h.run('addExerciseEditor = exercise => exerciseEditors.append(exercise); saveWorkout = () => {throw Error("Must not save")};');
    h.get('#exercise-editors').append({name:'Existing draft'});
    h.context.window.confirm=()=>false;
    h.run('repeatLastWorkout()');
    assert.equal(h.get('#exercise-editors').children[0].name,'Existing draft');
    h.context.window.confirm=()=>true;
    h.run('repeatLastWorkout()');
    assert.equal(h.get('#exercise-editors').children.length,1);
    assert.equal(h.get('#exercise-editors').children[0].sets[0].reps,10);
    assert.equal(h.run('workoutForm.elements.date.value'),'2026-09-05');
    assert.equal(h.run('workoutForm.elements.notes.value'),'Today notes');
});

test('rep comparisons require equal weight and distinguish bodyweight from zero', () => {
    const h = workoutToolsHarness();
    assert.match(h.run('previousSetText({weightKg:90,reps:8},"90","10")'), /\+2 reps/);
    assert.doesNotMatch(h.run('previousSetText({weightKg:90,reps:8},"80","10")'), /\+2/);
    assert.match(h.run('previousSetText({weightKg:null,reps:8},"","7")'), /-1 reps/);
    assert.doesNotMatch(h.run('previousSetText({weightKg:null,reps:8},"0","10")'), /\+2/);
    assert.equal(h.run('previousSetText({weightKg:90,reps:8},"90","")'), 'Last: 90kg × 8');
});

test('an old previous-session response cannot replace a newer lookup', async () => {
    const h = workoutToolsHarness();
    h.get('#workout-dialog').open = true;
    let resolve;
    h.context.fetch = () => new Promise(done => { resolve = done; });
    const pending = h.run('loadPreviousWorkout("Chest","2026-09-05",0)');
    h.run('previousLookupId++');
    resolve({ok:true,status:200,json:async () => ({date:'2026-09-01',name:'Chest',exercises:[]})});
    await pending;
    assert.equal(h.run('previousWorkout'), null);
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

test('rest confirmation cancels by default, confirms explicitly and prevents duplicate prompts', async () => {
    const h = harness();
    const dialog = h.get('#rest-confirm-dialog');
    let close;
    let opened = 0;
    dialog.addEventListener = (event, callback) => { if (event === 'close') close = callback; };
    dialog.showModal = () => { opened++; };
    const cancelled = h.run('confirmRestConversion()');
    assert.equal(dialog.returnValue, 'cancel');
    assert.equal(await h.run('confirmRestConversion()'), false);
    assert.equal(opened, 1);
    close();
    assert.equal(await cancelled, false);
    const confirmed = h.run('confirmRestConversion()');
    dialog.returnValue = 'confirm'; close();
    assert.equal(await confirmed, true);
    const reopened = h.run('confirmRestConversion()');
    assert.equal(dialog.returnValue, 'cancel');
    close(); await reopened;
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

test('API failures expose only well-formed support references', () => {
    const h = harness();
    h.context.response = { headers: { get: () => '12345678-abcd-1234-abcd-123456789abc' } };
    assert.equal(h.run('requestReference(requestError(response, "Failed"))'),
        ' Reference: 12345678-abcd-1234-abcd-123456789abc');
    h.context.response = { headers: { get: () => 'untrusted-private-data' } };
    assert.equal(h.run('requestReference(requestError(response, "Failed"))'), '');
});
