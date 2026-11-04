/* OE Scene Editor 0.1 — browser UI and interaction. */
(() => {
"use strict";
const $=id=>document.getElementById(id),api=window.OE;
if(!api){$("status").textContent="Ошибка: ядро OE не загрузилось";return;}
const canvas=$("viewport"),storageKey="oe-scene-v1";
let scene=api.demo(),selectedId=null,playing=false,player=null,renderer=null,lastTime=0,frames=0,fpsStart=performance.now();
let orbit={yaw:.55,pitch:.42,distance:24,target:[0,1.2,1]};
const keys=new Set(),pointers=new Map();let pinchDistance=0,hadMulti=false;
function notify(s){$("status").textContent=s;}
try{renderer=new api.Renderer(canvas);notify("Редактор готов · нажми на здание или выбери объект в списке");}
catch(e){$("render-error").hidden=false;$("render-error").textContent=String(e.message||e);notify("3D недоступен: WebGL2 не поддерживается");$("play").disabled=true;}
function cleanCamera(raw){if(!raw||typeof raw!=="object")return;orbit.yaw=Number.isFinite(+raw.yaw)?+raw.yaw:.55;orbit.pitch=api.clamp(Number(raw.pitch)||.42,-1.25,1.25);orbit.distance=api.clamp(Number(raw.distance)||24,4,100);orbit.target=Array.isArray(raw.target)?[0,1,2].map(i=>api.clamp(Number(raw.target[i])||0,-500,500)):[0,1.2,1];}
try{const s=localStorage.getItem(storageKey);if(s){const d=JSON.parse(s);scene=api.validateScene(d);cleanCamera(d.camera);notify("Восстановлена сохранённая сцена");}}catch(e){console.warn("OE local data:",e);}
function chosen(){return scene.find(o=>o.id===selectedId)||null;}
function renderHierarchy(){
 const host=$("hierarchy-list");host.replaceChildren();$("entity-count").textContent=String(scene.length);
 const groups=new Map();for(const o of scene){if(!groups.has(o.group))groups.set(o.group,[]);groups.get(o.group).push(o);}
 for(const [group,items] of groups){
  const heading=document.createElement("div");heading.className="group-title";heading.textContent=group+" · "+items.length;host.appendChild(heading);
  for(const o of items){const b=document.createElement("button");b.className="entity"+(o.id===selectedId?" selected":"");b.type="button";b.textContent=(o.mesh==="roof"?"⌂ ":o.mesh==="cylinder"?"◯ ":"◼ ")+o.name;b.title=o.name;b.addEventListener("click",()=>select(o.id));host.appendChild(b);}
 }
}
function inputField(parent,label,value,kind,onChange,step="0.1"){
 const wrap=document.createElement("div"),l=document.createElement("label"),inp=document.createElement("input");
 l.textContent=label;inp.type=kind;inp.value=String(value);if(kind==="number"){inp.step=step;inp.inputMode="decimal";}
 inp.addEventListener("change",()=>{try{onChange(inp.value);renderHierarchy();notify("Изменения применены");}catch(e){notify("Ошибка ввода: "+e.message);}});
 wrap.append(l,inp);parent.appendChild(wrap);return inp;
}
function renderInspector(){
 const host=$("inspector-content");host.replaceChildren();const o=chosen();if(!o){const msg=document.createElement("p");msg.className="empty";msg.textContent="Выбери объект на сцене или в иерархии.";host.append(msg);return;}
 inputField(host,"Название",o.name,"text",v=>{o.name=v.trim().slice(0,70)||"Объект";renderInspector();});
 inputField(host,"Цвет материала",o.color,"color",v=>{o.color=v;});
 const posLabel=document.createElement("h3");posLabel.textContent="Позиция (X / Y / Z)";host.append(posLabel);
 const pg=document.createElement("div");pg.className="input-grid";host.append(pg);
 ["X","Y","Z"].forEach((axis,i)=>inputField(pg,axis,o.position[i],"number",v=>{o.position[i]=api.clamp(Number(v)||0,-2000,2000);}));
 const r=document.createElement("h3");r.textContent="Поворот";host.append(r);
 inputField(host,"Y (градусы)",o.yaw,"number",v=>{o.yaw=api.clamp(Number(v)||0,-36000,36000);},"5");
 const scl=document.createElement("h3");scl.textContent="Масштаб (X / Y / Z)";host.append(scl);
 const sg=document.createElement("div");sg.className="input-grid";host.append(sg);
 ["X","Y","Z"].forEach((axis,i)=>inputField(sg,axis,o.scale[i],"number",v=>{o.scale[i]=api.clamp(Number(v)||1,.05,1000);}));
 const remove=document.createElement("button");remove.type="button";remove.className="danger delete";remove.textContent="Удалить объект";remove.addEventListener("click",()=>{scene=scene.filter(e=>e.id!==o.id);select(null);notify("Объект удалён");});host.append(remove);
}
function select(id){selectedId=id;renderHierarchy();renderInspector();}
function serialize(){return {format:"orvetra-scene",version:1,objects:scene,camera:orbit};}
function save(){try{localStorage.setItem(storageKey,JSON.stringify(serialize()));notify("Сцена сохранена в браузере ✓");}catch(e){notify("Не удалось сохранить: "+e.message);}}
function download(){const blob=new Blob([JSON.stringify(serialize(),null,2)],{type:"application/json"}),url=URL.createObjectURL(blob),a=document.createElement("a");a.href=url;a.download="orvetra-scene.json";document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),2000);notify("Сцена экспортирована в JSON");}
function addPrimitive(type){if(scene.length>=990){notify("Лимит демо — 990 объектов");return;}const o=api.createPrimitive(type);scene.push(o);select(o.id);notify("Добавлен примитив");}
function addBuilding(kind){if(scene.length>930){notify("Недостаточно места для нового здания");return;}const n=scene.filter(o=>o.name==="Главный фасад").length,offset=Math.floor(n/3)*10;
 const objs=api.makeBuilding(kind,Math.floor(n%3)*8-8,7+offset);scene.push(...objs);select(objs[0].id);notify("Здание добавлено. Для перемещения меняй координаты его частей.");}
function reset(){if(!confirm("Сбросить текущую сцену? Несохранённые изменения будут потеряны."))return;scene=api.demo();orbit={yaw:.55,pitch:.42,distance:24,target:[0,1.2,1]};selectedId=null;player=null;playing=false;keys.clear();$("play").textContent="▶ Играть";$("mode").textContent="РЕДАКТОР";$("touch-controls").hidden=true;try{localStorage.removeItem(storageKey);}catch(e){}select(null);notify("Загружена демо-сцена Орветры");}
function setPlay(active){playing=active;keys.clear();player=active?{x:0,z:12}:null;$("play").textContent=active?"■ Стоп":"▶ Играть";$("mode").textContent=active?"ИГРОВОЙ РЕЖИМ":"РЕДАКТОР";$("touch-controls").hidden=!active;$("tip").textContent=active?"WASD / стрелки / экранные кнопки — движение":"Проведи пальцем — вращать · колесо / два пальца — масштаб";if(!active)orbit.target=[0,1.2,1];notify(active?"Режим игры: управление тестовым персонажем":"Режим редактирования");}
$("add-tavern").addEventListener("click",()=>addBuilding("tavern"));
$("add-forge").addEventListener("click",()=>addBuilding("forge"));
$("add-auction").addEventListener("click",()=>addBuilding("auction"));
$("add-box").addEventListener("click",()=>addPrimitive("box"));
$("add-roof").addEventListener("click",()=>addPrimitive("roof"));
$("add-pillar").addEventListener("click",()=>addPrimitive("cylinder"));
$("save").addEventListener("click",save);$("export").addEventListener("click",download);
$("import").addEventListener("click",()=>$("import-file").click());
$("import-file").addEventListener("change",async e=>{const file=e.target.files?.[0];if(!file)return;if(file.size>3*1024*1024){notify("Файл слишком большой (лимит 3 МБ)");return;}try{const d=JSON.parse(await file.text());scene=api.validateScene(d);cleanCamera(d.camera);select(null);notify("Сцена импортирована: "+scene.length+" объектов");}catch(err){notify("Ошибка импорта: "+err.message);}finally{e.target.value="";}});
$("play").addEventListener("click",()=>setPlay(!playing));$("reset").addEventListener("click",reset);
canvas.addEventListener("pointerdown",e=>{e.preventDefault();canvas.setPointerCapture(e.pointerId);pointers.set(e.pointerId,{x:e.clientX,y:e.clientY,sx:e.clientX,sy:e.clientY,moved:false});if(pointers.size>1)hadMulti=true;});
canvas.addEventListener("pointermove",e=>{const p=pointers.get(e.pointerId);if(!p)return;e.preventDefault();const dx=e.clientX-p.x,dy=e.clientY-p.y;p.x=e.clientX;p.y=e.clientY;if(Math.hypot(p.x-p.sx,p.y-p.sy)>6)p.moved=true;
 if(pointers.size===1){orbit.yaw-=dx*.007;orbit.pitch=api.clamp(orbit.pitch+dy*.006,-1.2,1.25);}
 else if(pointers.size===2){const a=[...pointers.values()],dist=Math.hypot(a[0].x-a[1].x,a[0].y-a[1].y);if(pinchDistance>0)orbit.distance=api.clamp(orbit.distance*pinchDistance/Math.max(dist,1),4,100);pinchDistance=dist;}
});
function endPointer(e){const p=pointers.get(e.pointerId);if(!p)return;pointers.delete(e.pointerId);if(pointers.size<2)pinchDistance=0;if(pointers.size===0){if(!hadMulti&&!p.moved&&renderer&&!playing){const rect=canvas.getBoundingClientRect();select(renderer.pick(scene,e.clientX-rect.left,e.clientY-rect.top));}hadMulti=false;}}
canvas.addEventListener("pointerup",endPointer);canvas.addEventListener("pointercancel",endPointer);
canvas.addEventListener("wheel",e=>{e.preventDefault();orbit.distance=api.clamp(orbit.distance*Math.exp(e.deltaY*.001),4,100);},{passive:false});
canvas.addEventListener("contextmenu",e=>e.preventDefault());
function normalizeKey(k){const low=k.toLowerCase();return {arrowup:"w",arrowleft:"a",arrowdown:"s",arrowright:"d"}[low]||low;}
window.addEventListener("keydown",e=>{if(["INPUT","TEXTAREA"].includes(document.activeElement?.tagName))return;const k=normalizeKey(e.key);if(playing&&["w","a","s","d"].includes(k)){keys.add(k);e.preventDefault();}if(!playing&&(k==="delete"||k==="backspace")&&selectedId!==null){scene=scene.filter(o=>o.id!==selectedId);select(null);e.preventDefault();}});
window.addEventListener("keyup",e=>keys.delete(normalizeKey(e.key)));
window.addEventListener("blur",()=>keys.clear());
document.querySelectorAll("[data-key]").forEach(b=>{const key=b.dataset.key;b.addEventListener("pointerdown",e=>{e.preventDefault();keys.add(key);b.setPointerCapture(e.pointerId);});for(const ev of ["pointerup","pointercancel","lostpointercapture"])b.addEventListener(ev,()=>keys.delete(key));});
function step(dt){if(playing&&player){const side=(keys.has("d")?1:0)-(keys.has("a")?1:0),forward=(keys.has("w")?1:0)-(keys.has("s")?1:0),length=Math.hypot(side,forward)||1,s=dt*5/length;player.x=api.clamp(player.x+(side*Math.cos(orbit.yaw)-forward*Math.sin(orbit.yaw))*s,-35,35);player.z=api.clamp(player.z+(-side*Math.sin(orbit.yaw)-forward*Math.cos(orbit.yaw))*s,-35,35);orbit.target=[player.x,1.35,player.z];}}
function frame(time){const dt=Math.min(Math.max((time-lastTime)/1000,0),.05);lastTime=time;step(dt);if(renderer){renderer.render(scene,orbit,selectedId,player);frames++;if(time-fpsStart>=1000){$("fps").textContent=Math.round(frames*1000/(time-fpsStart))+" FPS";frames=0;fpsStart=time;}}requestAnimationFrame(frame);}
select(null);requestAnimationFrame(frame);
})();
