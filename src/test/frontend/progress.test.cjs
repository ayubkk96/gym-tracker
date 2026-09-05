const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '../../main/resources/static');
function harness() {
    const nodes = new Map();
    const element = () => ({value:'',textContent:'',children:[],addEventListener(){},
        replaceChildren(){this.children=[];},append(...items){this.children.push(...items);},setAttribute(){}});
    const get = key => {if(!nodes.has(key)) nodes.set(key,element());return nodes.get(key);};
    const context = vm.createContext({document:{querySelector:get,createElement:element,createElementNS:element},
        dateInput:{value:'2026-09-05'},Date,URLSearchParams,requireAuthenticated(){},requestReference(){return '';},
        requestError(){return Error('Failed');},fetch(){throw Error('Unexpected fetch');}});
    vm.runInContext(fs.readFileSync(path.join(root,'progress.js'),'utf8'),context);
    return {context,get,run:code=>vm.runInContext(code,context)};
}
test('chart x positions preserve missing dates and constant values stay finite',()=>{
    const h=harness();
    const plot=h.run('plotPoints([{date:"2026-09-01",value:93},{date:"2026-09-05",value:93}],"2026-09-01","2026-09-09")');
    assert.equal(plot.points[1].x-plot.points[0].x,212);
    assert.ok(plot.points.every(p=>Number.isFinite(p.y)));
    assert.equal(plot.points.length,2);
});
test('zero kilograms is labelled as weight, not bodyweight',()=>{
    const h=harness();
    assert.equal(h.run('recordCard("Record",{date:"2026-09-01",weightKg:0,reps:8}).children[1].textContent'),'0kg × 8');
    assert.equal(h.run('recordCard("Record",{date:"2026-09-01",weightKg:null,reps:8}).children[1].textContent'),'BW × 8');
});
test('stale progress responses cannot replace a newer selection',async()=>{
    const h=harness();let resolve;
    h.context.fetch=()=>new Promise(done=>{resolve=done;});
    const pending=h.run('loadProgress()');
    h.run('progressRequest++');
    resolve({ok:true,json:async()=>({})});await pending;
    assert.equal(h.get('#progress-charts').children.length,0);
});
test('failed progress requests offer retry without keeping old charts',async()=>{
    const h=harness();h.context.fetch=async()=>({ok:false});
    await h.run('loadProgress()');
    assert.match(h.get('#progress-status').textContent,/Refresh progress/);
    assert.equal(h.get('#progress-charts').children.length,0);
});
