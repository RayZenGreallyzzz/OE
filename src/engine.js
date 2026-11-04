/* OE 0.1 — small, dependency-free WebGL2 renderer. MIT; see LICENSE. */
(() => {
"use strict";
const palette = {stone:"#817d79",wood:"#71452e",timber:"#443026",roof:"#933f38",sand:"#c8a47d",gold:"#d5a65b",iron:"#434c57",blue:"#34435c",grass:"#354e40"};
const clamp=(v,min,max)=>Math.max(min,Math.min(max,v));
const vecSub=(a,b)=>[a[0]-b[0],a[1]-b[1],a[2]-b[2]];
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
function unit(v){const l=Math.hypot(...v)||1;return v.map(x=>x/l);}
function identity(){return [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];}
function multiply(a,b){const m=new Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++){let s=0;for(let k=0;k<4;k++)s+=a[k*4+r]*b[c*4+k];m[c*4+r]=s;}return m;}
function translation(v){const m=identity();m[12]=v[0];m[13]=v[1];m[14]=v[2];return m;}
function scaling(v){return [v[0],0,0,0,0,v[1],0,0,0,0,v[2],0,0,0,0,1];}
function yawMatrix(deg){const a=deg*Math.PI/180,c=Math.cos(a),s=Math.sin(a);return [c,0,-s,0,0,1,0,0,s,0,c,0,0,0,0,1];}
function perspective(fov,aspect,near,far){const f=1/Math.tan(fov/2),nf=1/(near-far);return [f/aspect,0,0,0,0,f,0,0,0,0,(far+near)*nf,-1,0,0,2*far*near*nf,0];}
function lookAt(eye,center){const f=unit(vecSub(center,eye)),s=unit(cross(f,[0,1,0])),u=cross(s,f);return [s[0],u[0],-f[0],0,s[1],u[1],-f[1],0,s[2],u[2],-f[2],0,-dot(s,eye),-dot(u,eye),dot(f,eye),1];}
function transform(m,v){return [m[0]*v[0]+m[4]*v[1]+m[8]*v[2]+m[12]*v[3],m[1]*v[0]+m[5]*v[1]+m[9]*v[2]+m[13]*v[3],m[2]*v[0]+m[6]*v[1]+m[10]*v[2]+m[14]*v[3],m[3]*v[0]+m[7]*v[1]+m[11]*v[2]+m[15]*v[3]];}
function colorBytes(hex){const s=/^#[0-9a-f]{6}$/i.test(hex)?hex:"#d0a46e";return [1,3,5].map(i=>parseInt(s.slice(i,i+2),16)/255);}
function meshData(kind){
 const out=[];
 function tri(a,b,c){const n=unit(cross(vecSub(b,a),vecSub(c,a)));for(const p of [a,b,c])out.push(...p,...n);}
 function quad(a,b,c,d){tri(a,b,c);tri(a,c,d);}
 if(kind==="roof"){
   const A=[-.5,0,-.5],B=[.5,0,-.5],C=[0,.78,-.5],D=[-.5,0,.5],E=[.5,0,.5],F=[0,.78,.5];
   tri(A,C,B);tri(D,E,F);quad(D,F,C,A);quad(B,C,F,E);quad(A,B,E,D);
 } else if(kind==="cylinder"){
   const N=16;for(let i=0;i<N;i++){const a=i*2*Math.PI/N,b=(i+1)*2*Math.PI/N,lo=[Math.cos(a)*.5,-.5,Math.sin(a)*.5],hi=[lo[0],.5,lo[2]],lo2=[Math.cos(b)*.5,-.5,Math.sin(b)*.5],hi2=[lo2[0],.5,lo2[2]];quad(lo,hi,hi2,lo2);tri([0,.5,0],hi,hi2);tri([0,-.5,0],lo2,lo);}
 } else {
   const x=.5,y=.5,z=.5;
   quad([-x,-y,z],[x,-y,z],[x,y,z],[-x,y,z]);
   quad([x,-y,-z],[-x,-y,-z],[-x,y,-z],[x,y,-z]);
   quad([-x,-y,-z],[-x,-y,z],[-x,y,z],[-x,y,-z]);
   quad([x,-y,z],[x,-y,-z],[x,y,-z],[x,y,z]);
   quad([-x,y,z],[x,y,z],[x,y,-z],[-x,y,-z]);
   quad([-x,-y,-z],[x,-y,-z],[x,-y,z],[-x,-y,z]);
 }
 return new Float32Array(out);
}
const VERT=`#version 300 es
precision highp float;
in vec3 aPos;in vec3 aNormal;
uniform mat4 uMvp;uniform mat4 uModel;
out vec3 vNormal;out vec3 vWorld;
void main(){vec4 w=uModel*vec4(aPos,1.0);vWorld=w.xyz;vNormal=mat3(uModel)*aNormal;gl_Position=uMvp*vec4(aPos,1.0);}
`;
const FRAG=`#version 300 es
precision mediump float;
in vec3 vNormal;in vec3 vWorld;
uniform vec3 uColor;uniform vec3 uEye;uniform float uSelected;uniform float uGrid;
out vec4 fragColor;
void main(){
 vec3 n=normalize(vNormal);
 float shade=max(dot(n,normalize(vec3(-0.45,1.0,0.55))),0.0);
 vec3 base=uColor*(0.49+0.51*shade);
 base+=0.10*pow(max(dot(reflect(-normalize(vec3(-0.45,1.0,0.55)),n),normalize(uEye-vWorld)),0.0),12.0);
 if(uGrid>0.5){vec2 g=abs(fract(vWorld.xz+0.5)-0.5);float line=1.0-step(0.025,min(g.x,g.y));base=mix(base,vec3(0.30,0.35,0.40),line*0.35);}
 base=mix(base,vec3(1.0,0.74,0.28),uSelected*0.23);
 float fog=smoothstep(45.0,110.0,length(uEye-vWorld));
 fragColor=vec4(mix(base,vec3(0.085,0.11,0.16),fog),1.0);
}
`;
function compile(gl,type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
function program(gl){const p=gl.createProgram(),v=compile(gl,gl.VERTEX_SHADER,VERT),f=compile(gl,gl.FRAGMENT_SHADER,FRAG);gl.attachShader(p,v);gl.attachShader(p,f);gl.linkProgram(p);gl.deleteShader(v);gl.deleteShader(f);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;}
class Renderer{
 constructor(canvas){
   this.canvas=canvas;const gl=canvas.getContext("webgl2",{alpha:false,antialias:true,powerPreference:"low-power"});
   if(!gl)throw Error("WebGL2 недоступен. Используй Chrome/Firefox с включённым аппаратным ускорением.");
   this.gl=gl;this.program=program(gl);this.eye=[0,8,17];this.viewProjection=identity();
   this.u={mvp:gl.getUniformLocation(this.program,"uMvp"),model:gl.getUniformLocation(this.program,"uModel"),color:gl.getUniformLocation(this.program,"uColor"),eye:gl.getUniformLocation(this.program,"uEye"),selected:gl.getUniformLocation(this.program,"uSelected"),grid:gl.getUniformLocation(this.program,"uGrid")};
   this.geometries={};gl.enable(gl.DEPTH_TEST);gl.enable(gl.CULL_FACE);gl.cullFace(gl.BACK);
   for(const kind of ["box","roof","cylinder"]){const data=meshData(kind),vao=gl.createVertexArray(),buf=gl.createBuffer();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buf);gl.bufferData(gl.ARRAY_BUFFER,data,gl.STATIC_DRAW);const a=gl.getAttribLocation(this.program,"aPos"),n=gl.getAttribLocation(this.program,"aNormal");gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,3,gl.FLOAT,false,24,0);gl.enableVertexAttribArray(n);gl.vertexAttribPointer(n,3,gl.FLOAT,false,24,12);this.geometries[kind]={vao,count:data.length/6};}
   gl.bindVertexArray(null);
 }
 resize(){const dpr=Math.min(window.devicePixelRatio||1,1.5),w=Math.max(1,Math.round(this.canvas.clientWidth*dpr)),h=Math.max(1,Math.round(this.canvas.clientHeight*dpr));if(this.canvas.width!==w||this.canvas.height!==h){this.canvas.width=w;this.canvas.height=h;this.gl.viewport(0,0,w,h);}}
 camera(orbit){const cp=Math.cos(orbit.pitch);this.eye=[orbit.target[0]+Math.sin(orbit.yaw)*cp*orbit.distance,orbit.target[1]+Math.sin(orbit.pitch)*orbit.distance,orbit.target[2]+Math.cos(orbit.yaw)*cp*orbit.distance];this.viewProjection=multiply(perspective(Math.PI/3,this.canvas.width/this.canvas.height,.1,170),lookAt(this.eye,orbit.target));}
 drawObject(o,selected=false){const gl=this.gl,g=this.geometries[o.mesh]||this.geometries.box,m=multiply(multiply(translation(o.position),yawMatrix(o.yaw||0)),scaling(o.scale));gl.uniformMatrix4fv(this.u.model,false,m);gl.uniformMatrix4fv(this.u.mvp,false,multiply(this.viewProjection,m));gl.uniform3fv(this.u.color,colorBytes(o.color));gl.uniform1f(this.u.selected,selected?1:0);gl.uniform1f(this.u.grid,o.grid?1:0);gl.bindVertexArray(g.vao);gl.drawArrays(gl.TRIANGLES,0,g.count);}
 render(objects,orbit,selectedId,player){this.resize();this.camera(orbit);const gl=this.gl;gl.clearColor(.07,.095,.135,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.useProgram(this.program);gl.uniform3fv(this.u.eye,this.eye);for(const o of objects)this.drawObject(o,o.id===selectedId);if(player)this.drawObject({mesh:"cylinder",position:[player.x,1.0,player.z],scale:[.55,2,.55],color:"#dcb46b",yaw:0});gl.bindVertexArray(null);}
 project(pos){const q=transform(this.viewProjection,[...pos,1]);if(q[3]<=0)return null;const nx=q[0]/q[3],ny=q[1]/q[3];return [(nx*.5+.5)*this.canvas.clientWidth,(1-(ny*.5+.5))*this.canvas.clientHeight,q[3]];}
 pick(objects,px,py){let best=null,score=Infinity;for(const o of objects){if(o.grid)continue;const p=this.project(o.position);if(!p)continue;const maxDim=Math.max(...o.scale),radius=clamp(maxDim*420/p[2],12,100);const d=Math.hypot(px-p[0],py-p[1]);if(d<radius&&d/radius<score){score=d/radius;best=o.id;}}return best;}
}
let nextId=1;function part(name,mesh,pos,scale,color,group,grid=false){return {id:nextId++,name,mesh,position:pos,scale,color,yaw:0,group,grid};}
function makeBuilding(kind,x,z){
 const group=kind==="tavern"?"Таверна":kind==="forge"?"Кузница":"Аукцион";
 const roof=kind==="forge"?"#454e59":kind==="auction"?"#514468":"#994d39";
 const wall=kind==="forge"?"#7b7976":kind==="auction"?"#d7bea1":"#c5aa84";
 const items=[],add=(n,m,dx,y,dz,sx,sy,sz,c)=>items.push(part(n,m,[x+dx,y,z+dz],[sx,sy,sz],c,group));
 add("Каменный фундамент","box",0,.17,0,6,.34,5,palette.stone);
 add("Главный фасад","box",0,1.77,0,5.7,2.95,4.8,wall);
 add("Черепичная крыша","roof",0,3.2,0,6.5,2.0,5.5,roof);
 add("Дверь","box",0,1.05,2.44,1.3,2.1,.1,palette.wood);
 add("Фурнитура","box",.4,1.0,2.51,.11,.12,.1,palette.gold);
 for(const dx of [-1.85,1.85]){
   add("Деревянная балка","box",dx,1.65,2.49,.23,3.15,.23,palette.timber);
   add("Окно","box",dx,1.98,2.57,.85,.93,.08,kind==="forge"?"#d28c48":"#e6c987");
   add("Переплёт окна","box",dx,1.98,2.63,.11,.95,.09,palette.timber);
   add("Карниз окна","box",dx,2.48,2.63,1.0,.12,.12,palette.timber);
 }
 add("Фасадная балка","box",0,3.1,2.51,5.95,.19,.22,palette.timber);
 if(kind==="forge"){
   add("Труба кузницы","box",1.9,4.55,-.7,1.2,3.6,1.1,"#58575b");
   add("Устье трубы","box",1.9,6.35,-.7,1.45,.3,1.35,palette.iron);
 } else if(kind==="auction"){
   for(const dx of [-2.45,2.45]){
     add("Колонна","cylinder",dx,1.55,2.63,.43,2.95,.43,palette.stone);
     add("Капитель","box",dx,3.1,2.63,.78,.3,.76,palette.gold);
   }
   add("Вывеска аукциона","box",0,2.65,2.62,1.55,.5,.16,"#bd9359");
 } else {
   add("Балкон","box",0,2.45,2.7,2.2,.2,.8,palette.wood);
   add("Труба таверны","box",-1.9,4.35,-.9,.75,2.1,.8,palette.stone);
 }
 return items;
}
function demo(){
 nextId=1;const a=[];
 a.push(part("Земля","box",[0,-.19,0],[75,.3,75],palette.grass,"Мир",true));
 a.push(part("Мощёная площадь","box",[0,-.015,2],[23,.1,19],"#777775","Мир",true));
 a.push(part("Главная улица","box",[0,.055,15],[8,.12,24],"#97928b","Мир",true));
 a.push(...makeBuilding("tavern",-8,-6),...makeBuilding("forge",0,-9),...makeBuilding("auction",8,-6));
 function lantern(x,z){const g="Освещение";a.push(part("Фонарный столб","cylinder",[x,1.45,z],[.17,2.9,.17],"#313b48",g));a.push(part("Лампа","box",[x,3,z],[.55,.65,.55],"#f1bc6b",g));a.push(part("Навершие","roof",[x,3.35,z],[.75,.42,.75],"#292c37",g));}
 for(const [x,z] of [[-5,5],[5,5],[-5,12],[5,12]])lantern(x,z);
 a.push(part("Фонтан","cylinder",[0,.3,4.6],[3.1,.65,3.1],"#64778b","Площадь"));
 a.push(part("Вода","cylinder",[0,.67,4.6],[2.45,.08,2.45],"#4d9db7","Площадь"));
 a.push(part("Чаша","cylinder",[0,1,4.6],[1.05,.9,1.05],"#9da1a2","Площадь"));
 return a;
}
function validateScene(raw){
 if(!raw||!Array.isArray(raw.objects)||raw.objects.length>1000)throw Error("Неверный файл сцены или слишком много объектов (максимум 1000).");
 const used=new Set();let id=1;
 const objects=raw.objects.map(o=>{if(!o||typeof o!=="object")throw Error("Некорректный объект.");
 const clean={id:Number.isSafeInteger(o.id)&&o.id>0&&!used.has(o.id)?o.id:null,name:String(o.name||"Объект").slice(0,70),group:String(o.group||"Импорт").slice(0,70),mesh:["box","roof","cylinder"].includes(o.mesh)?o.mesh:"box",position:[0,1,2].map(i=>clamp(Number(o.position?.[i])||0,-2000,2000)),scale:[0,1,2].map(i=>clamp(Number(o.scale?.[i])||1,.05,1000)),yaw:clamp(Number(o.yaw)||0,-36000,36000),color:/^#[0-9a-f]{6}$/i.test(o.color)?o.color:"#bd9462",grid:Boolean(o.grid)};
 if(clean.id===null){while(used.has(id))id++;clean.id=id++;}used.add(clean.id);return clean;});
 nextId=Math.max(1,...used)+1;return objects;
}
function createPrimitive(kind){return part(kind==="roof"?"Новая крыша":kind==="cylinder"?"Новая колонна":"Новый куб",kind,[0,kind==="roof"?1.5:1,7],kind==="roof"?[3,1.5,3]:[1.5,2,1.5],kind==="roof"?"#9e4e44":"#c3a37b","Новые объекты");}
window.OE={Renderer,makeBuilding,demo,validateScene,createPrimitive,clamp,multiply,identity,transform,projectVersion:"0.1.0"};
})();
