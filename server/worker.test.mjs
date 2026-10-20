import worker from "./worker.js";
import assert from "node:assert/strict";

class KV { constructor(){this.m=new Map();} async get(k){return this.m.has(k)?this.m.get(k):null;} async put(k,v){this.m.set(k,v);} }
const env = { BADGES: new KV(), ADMIN_PASSWORD: "s3cret!&" };
const call = (method, path, body, ip="1.1.1.1") => worker.fetch(new Request("https://x.dev"+path, {
  method, headers: {"cf-connecting-ip": ip, "content-type":"application/json"}, body: body ? JSON.stringify(body) : undefined }), env);
const admin = (b, ip) => call("POST","/admin",{password:"s3cret!&",...b},ip);

let r = await call("GET","/badges"); assert.equal(r.status,200); assert.deepEqual(await r.json(),{badges:{},update:null});
r = await admin({action:"check"}); assert.equal(r.status,200);
r = await admin({action:"grant",user:"@NoizeMC010",color:"#ff3b30",reason:"за поддержку"}); assert.equal(r.status,200);
let j = await (await call("GET","/badges")).json();
assert.deepEqual(Object.keys(j.badges),["noizemc010"]); assert.equal(j.badges.noizemc010.c,"#FF3B30"); assert.equal(j.badges.noizemc010.r,"за поддержку");
r = await admin({action:"grant",user:"bad user",color:"#ff3b30"}); assert.equal(r.status,400);
r = await admin({action:"grant",user:"ok",color:"red"}); assert.equal(r.status,400);
r = await admin({action:"set_update",build:12,url:"http://evil",notes:""}); assert.equal(r.status,400);
r = await admin({action:"set_update",build:12,url:"https://github.com/a/b/releases/download/t/x.apk",notes:"новое"}); assert.equal(r.status,200);
j = await (await call("GET","/badges")).json(); assert.equal(j.update.build,12);
r = await admin({action:"clear_update"}); j = await r.json(); assert.equal(j.update,null);
r = await admin({action:"revoke",user:"noizemc010"}); j = await r.json(); assert.deepEqual(j.badges,{});
r = await admin({action:"nope"}); assert.equal(r.status,400);

// wrong password: refused, no change, and blocked after 8 tries (per IP)
for (let i=0;i<8;i++){ r = await call("POST","/admin",{password:"wrong",action:"grant",user:"hack",color:"#000000"},"9.9.9.9"); assert.equal(r.status,403); }
r = await call("POST","/admin",{password:"s3cret!&",action:"check"},"9.9.9.9"); assert.equal(r.status,429);
r = await admin({action:"check"},"2.2.2.2"); assert.equal(r.status,200);   // other IP unaffected
j = await (await call("GET","/badges")).json(); assert.ok(!j.badges.hack);
// no password configured -> admin is off
const saved = env.ADMIN_PASSWORD; env.ADMIN_PASSWORD = undefined;
r = await admin({action:"check"},"3.3.3.3"); assert.equal(r.status,500); env.ADMIN_PASSWORD = saved;
r = await call("GET","/nothing"); assert.equal(r.status,404);
console.log("SERVER ALL PASSED");
