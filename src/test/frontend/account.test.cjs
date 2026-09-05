const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
function harness() {
    const nodes=new Map();
    const get=id=>{if(!nodes.has(id)) nodes.set(id,{textContent:'',disabled:false,addEventListener(){}});return nodes.get(id);};
    const fields=[{value:'secret'}, {value:'DELETE'}]; fields.password=fields[0];fields.confirmation=fields[1];
    get('#delete-account-form').elements=fields;
    get('#delete-account-form').reportValidity=()=>true;
    const context=vm.createContext({document:{querySelector:get},window:{location:{replace(){}}},
        mutationHeaders:()=>({'X-CSRF-TOKEN':'test'}),requireAuthenticated(){},requestError:()=>Error('Failed'),requestReference:()=>'',
        fetch:()=>{throw Error('Unexpected request');}});
    vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/account.js'),'utf8'),context);
    return {context,get,fields,run:code=>vm.runInContext(code,context)};
}
test('delete sends confirmed credentials with CSRF then signs out and clears password',async()=>{
    const h=harness();let sent,redirect;
    h.context.fetch=async(url,options)=>{sent={url,options};return {ok:true,status:204};};
    h.context.window.location.replace=url=>{redirect=url;};
    await h.run('deleteAccount({preventDefault(){}})');
    assert.equal(sent.url,'/api/account');assert.equal(sent.options.method,'DELETE');
    assert.equal(sent.options.headers['X-CSRF-TOKEN'],'test');
    assert.deepEqual(JSON.parse(sent.options.body),{password:'secret',confirmation:'DELETE'});
    assert.equal(redirect,'/login.html');assert.equal(h.fields.password.value,'');
});
test('invalid confirmation form makes no deletion request',async()=>{
    const h=harness();h.get('#delete-account-form').reportValidity=()=>false;
    await h.run('deleteAccount({preventDefault(){}})');
    assert.equal(h.run('accountBusy'),false);
});
test('failed deletion stays open and clears credentials without automatic retry',async()=>{
    const h=harness();let calls=0;
    h.context.fetch=async()=>{calls++;return {ok:false,status:403};};
    await h.run('deleteAccount({preventDefault(){}})');
    assert.equal(calls,1);assert.match(h.get('#account-status').textContent,/not authorised/);
    assert.equal(h.fields.password.value,'');assert.equal(h.run('accountBusy'),false);
});
