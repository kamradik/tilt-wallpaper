// Renders effects to a PNG contact sheet for visual review: each effect on a portrait (the author avatar) and a
// painted landscape, at three tilts. The shaders are assembled exactly as GlScene does (common + effect + main).
// Usage: PLAYWRIGHT_MODULE=<path to playwright> node tests/fx-render.cjs <out.png> effect [effect...] [--time=3.2]
const fs=require('fs');
const path=require('path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const root=path.join(__dirname,'..');
const dir=path.join(root,'app/src/main/assets/fx');
const args=process.argv.slice(2);const out=args.shift();
const time=parseFloat((args.find(a=>a.startsWith('--time='))||'--time=3.2').slice(7));
const names=args.filter(a=>!a.startsWith('--'));
const mods=((args.find(a=>a.startsWith('--mods='))||'--mods=0,0,0,0').slice(7)).split(',').map(Number); // grain, vignette, glare, tone -1..1
const quad=fs.readFileSync(path.join(root,'app/src/main/assets/quad.glsl'),'utf8');
const common=fs.readFileSync(path.join(dir,'common.glsl'),'utf8'),main=fs.readFileSync(path.join(dir,'main.glsl'),'utf8');
const effects=names.map(n=>[n,common+'\n'+fs.readFileSync(path.join(dir,n+'.glsl'),'utf8')+'\n'+main]);
const avatar='data:image/jpeg;base64,'+fs.readFileSync(path.join(root,'app/src/main/res/drawable-nodpi/author_avatar.jpg')).toString('base64');
(async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||(process.platform==='win32'?'C:/Program Files/Google/Chrome/Application/chrome.exe':undefined),headless:true,args:['--enable-unsafe-swiftshader']});
  try{
    const page=await browser.newPage();
    const png=await page.evaluate(async({quad,effects,avatar,time,mods})=>{
      const W=270,H=585,tilts=[[0,0],[.55,-.3],[-.55,.35]];
      // Landscape: sky, sun, layered hills, a lake, a tree: enough structure to judge refraction and parallax.
      const land=document.createElement('canvas');land.width=720;land.height=1560;const g=land.getContext('2d');
      let gr=g.createLinearGradient(0,0,0,900);gr.addColorStop(0,'#2d5fa8');gr.addColorStop(.7,'#f2b37a');gr.addColorStop(1,'#f7d6a0');g.fillStyle=gr;g.fillRect(0,0,720,900);
      g.fillStyle='#fff3c4';g.beginPath();g.arc(470,560,70,0,7);g.fill();
      const hill=(y,a,col)=>{g.fillStyle=col;g.beginPath();g.moveTo(0,1560);for(let x=0;x<=720;x+=10)g.lineTo(x,y+Math.sin(x/90+a)*40+Math.sin(x/37+a*2)*14);g.lineTo(720,1560);g.fill();};
      hill(700,0,'#5a6f8a');hill(800,2,'#3d5a4a');hill(900,4,'#2a4434');
      gr=g.createLinearGradient(0,980,0,1560);gr.addColorStop(0,'#6fa3c9');gr.addColorStop(1,'#1d3c57');g.fillStyle=gr;g.fillRect(0,980,720,580);
      for(let i=0;i<30;i++){g.strokeStyle='rgba(255,255,255,.18)';g.beginPath();const y=1000+i*18;g.moveTo(40+i*7,y);g.lineTo(300+i*9,y);g.stroke();}
      g.fillStyle='#3b2a1a';g.fillRect(150,700,18,330);g.fillStyle='#244a2c';g.beginPath();g.arc(159,660,90,0,7);g.fill();
      g.fillStyle='#e9e2d0';g.fillRect(520,860,90,120);g.fillStyle='#8a3b2b';g.beginPath();g.moveTo(505,860);g.lineTo(565,800);g.lineTo(625,860);g.fill();
      const img=new Image();img.src=avatar;await img.decode();
      const photoCanvas=document.createElement('canvas');photoCanvas.width=720;photoCanvas.height=1560;const pg=photoCanvas.getContext('2d');
      const s=Math.max(720/img.width,1560/img.height);pg.drawImage(img,(720-img.width*s)/2,(1560-img.height*s)/2,img.width*s,img.height*s);
      const photos=[photoCanvas,land];
      const canvas=document.createElement('canvas');canvas.width=W;canvas.height=H;
      const gl=canvas.getContext('webgl2',{preserveDrawingBuffer:true});
      const texFrom=(src)=>{const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,false);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,src);gl.generateMipmap(gl.TEXTURE_2D);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);return t;};
      const flat=(w,h,v)=>{const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array(w*h*4).fill(v));gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);return t;};
      const photoTex=photos.map(texFrom),wipe=flat(4,4,0),field=flat(72,160,0);
      // Depth maps as MlDepth writes them: R depth (white near), G subject mask, B 255 = has a mask. The portrait's
      // subject is an ellipse round the face; the landscape has no mask and gets nearer toward the bottom.
      const depthMap=(w,h,f)=>{const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);const px=new Uint8Array(w*h*4);for(let y=0;y<h;y++)for(let x=0;x<w;x++)px.set(f(x/w,y/h),(y*w+x)*4);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,px);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);return t;};
      const depths=[depthMap(180,390,(x,y)=>{const e=Math.hypot((x-.5)/.36,(y-.52)/.3);const m=Math.max(0,Math.min(1,(1-e)*14));const d=m>0?220:60+y*80;return[d,m*255,255,255];}),depthMap(90,195,(x,y)=>{const d=y<.45?20:40+(y-.45)*380;return[Math.min(255,d),0,0,255];})];
      const vao=gl.createVertexArray();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,gl.createBuffer());
      gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,0,0,1,-1,1,0,-1,1,0,1,-1,1,0,1,1,-1,1,0,1,1,1,1]),gl.STATIC_DRAW);
      gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,2,gl.FLOAT,false,16,0);gl.enableVertexAttribArray(1);gl.vertexAttribPointer(1,2,gl.FLOAT,false,16,8);
      const sheet=document.createElement('canvas');const cols=tilts.length*photos.length;sheet.width=cols*(W+6);sheet.height=effects.length*(H+24);
      const sc=sheet.getContext('2d');sc.fillStyle='#111';sc.fillRect(0,0,sheet.width,sheet.height);sc.font='16px sans-serif';
      const errors=[];
      effects.forEach(([name,source],row)=>{
        const p=gl.createProgram();let bad=false;
        for(const [type,src] of [[gl.VERTEX_SHADER,quad],[gl.FRAGMENT_SHADER,source]]){const sh=gl.createShader(type);gl.shaderSource(sh,src);gl.compileShader(sh);if(!gl.getShaderParameter(sh,gl.COMPILE_STATUS)){errors.push(name+': '+gl.getShaderInfoLog(sh));bad=true;}gl.attachShader(p,sh);}
        if(bad)return;gl.linkProgram(p);gl.useProgram(p);const u=n=>gl.getUniformLocation(p,n);
        photos.forEach((ph,pi)=>tilts.forEach(([tx,ty],ti)=>{
          [[gl.TEXTURE0,photoTex[pi],'u_image',0],[gl.TEXTURE1,depths[pi],'u_depth',1],[gl.TEXTURE2,wipe,'u_wipeMask',2],[gl.TEXTURE3,field,'u_field',3]].forEach(([unit,t,n,i])=>{gl.activeTexture(unit);gl.bindTexture(gl.TEXTURE_2D,t);gl.uniform1i(u(n),i);});
          const aspect=W/H,mm=70,deg=tx*65*.75,r=deg*Math.PI/180,sn=Math.sin(r),c=Math.cos(r);
          gl.uniform2f(u('u_imageSize'),720,1560);gl.uniform2f(u('u_coverScale'),Math.min(1,aspect/(720/1560)),Math.min(1,(720/1560)/aspect));gl.uniform2f(u('u_crop'),.5,.5);gl.uniform1f(u('u_zoom'),1.08);gl.uniform1f(u('u_aspect'),aspect);gl.uniform1f(u('u_screenWidthMm'),mm);
          gl.uniform3f(u('u_projectionX'),c,0,.5*(c-1));gl.uniform3f(u('u_projectionY'),0,1,0);gl.uniform3f(u('u_projectionZ'),-sn*mm/320,0,1-sn*mm*.5/320);gl.uniform3f(u('u_gap'),sn*mm,0,sn*mm*.5);gl.uniform3f(u('u_blurGap'),20,0,10);
          gl.uniform2f(u('u_tilt'),tx,ty);gl.uniform4f(u('u_touch'),.5,.5,99,0);gl.uniform2f(u('u_params'),.6,.5);gl.uniform1f(u('u_toggle'),1);gl.uniform1f(u('u_motion'),1);gl.uniform1f(u('u_time'),time+ti*.37);gl.uniform2f(u('u_wake'),9,0);gl.uniform1f(u('u_alpha'),1);
          gl.uniform3f(u('u_gravity'),-tx*.8,-.9,0);gl.uniform2f(u('u_charge'),0,.8);gl.uniform1f(u('u_day'),.6);gl.uniform4f(u('u_mods'),mods[0],mods[1],mods[2],mods[3]);
          gl.viewport(0,0,W,H);gl.clearColor(0,0,0,1);gl.clear(gl.COLOR_BUFFER_BIT);gl.drawArrays(gl.TRIANGLES,0,6);
          const x=(pi*tilts.length+ti)*(W+6);sc.drawImage(canvas,x,row*(H+24)+20);
        }));
        sc.fillStyle='#fff';sc.fillText(name,6,row*(H+24)+15);
      });
      return {png:sheet.toDataURL('image/png'),errors};
    },{quad,effects,avatar,time,mods});
    if(png.errors.length){console.error(png.errors.join('\n'));process.exitCode=1;}
    fs.writeFileSync(out,Buffer.from(png.png.split(',')[1],'base64'));console.log('wrote',out);
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
