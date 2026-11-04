const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const source = fs.readFileSync("src/engine.js", "utf8");
const sandbox = {window: {}, console};
vm.runInNewContext(source, sandbox);
const OE = sandbox.window.OE;

test("engine exposes the renderer and scene API", () => {
 assert.equal(typeof OE.Renderer, "function");
 assert.equal(typeof OE.demo, "function");
 assert.equal(typeof OE.validateScene, "function");
});

test("demo creates a complete city block with unique ids", () => {
 const objects = OE.demo();
 assert.ok(objects.length >= 60);
 for (const group of ["Таверна", "Кузница", "Аукцион", "Мир", "Освещение"]) {
   assert.ok(objects.some(o => o.group === group), "Missing group: " + group);
 }
 assert.equal(new Set(objects.map(o => o.id)).size, objects.length);
 assert.ok(objects.every(o => ["box", "roof", "cylinder"].includes(o.mesh)));
});

test("save/load keeps valid scene objects", () => {
 const original = OE.demo();
 const imported = OE.validateScene(JSON.parse(JSON.stringify({objects:original})));
 assert.equal(imported.length, original.length);
 assert.equal(imported[0].name, original[0].name);
 assert.equal(imported[10].color, original[10].color);
 const added = OE.createPrimitive("box");
 assert.ok(added.id > Math.max(...imported.map(o => o.id)));
});

test("scene loading limits object counts and sanitizes fields", () => {
 assert.throws(() => OE.validateScene({objects:new Array(1001).fill({})}));
 assert.throws(() => OE.validateScene({objects:[null]}));
 const arr = OE.validateScene({objects:[
  {id:7,name:"A",mesh:"invalid",position:[4,2,1],scale:[0,2,2],color:"red"},
  {id:7,name:"B",mesh:"roof",position:[5,2,0],scale:[1,1,1],color:"#abcdef"}
 ]});
 assert.equal(new Set(arr.map(o=>o.id)).size,2);
 assert.equal(arr[0].mesh,"box");
 assert.equal(arr[0].color,"#bd9462");
 assert.ok(arr[0].scale[0]>=.05);
 assert.equal(arr[1].color,"#abcdef");
});

test("building factory provides a roof and doors", () => {
 for (const type of ["tavern","forge","auction"]) {
  const o = OE.makeBuilding(type,0,0);
  assert.ok(o.some(x=>x.mesh==="roof"));
  assert.ok(o.some(x=>x.name==="Дверь"));
 }
});
