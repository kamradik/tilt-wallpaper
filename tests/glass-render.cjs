// Renders the interface's Liquid Glass (Ios.GLASS_AGSL) in WebGL over a test photo, for visual review and as a
// syntax check of the shader body. The AGSL source is read from Ios.java and translated mechanically (half -> float,
// the child shader -> a texture), so what is reviewed is the code the app ships.
// Usage: PLAYWRIGHT_MODULE=<path to playwright> node tests/glass-render.cjs <out.png> [--light=-115] [--touch=1] [--ripple=.4]
const fs=require('fs');
const path=require('path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const root=path.join(__dirname,'..');
const args=process.argv.slice(2);const out=args.shift();
const opt=(k,d)=>{const a=args.find(a=>a.startsWith('--'+k+'='));return a?parseFloat(a.slice(k.length+3)):d;};
const java=fs.readFileSync(path.join(root,'app/src/main/java/dev/solotilt/personal/Ios.java'),'utf8');
const m=java.match(/GLASS_AGSL=\s*([\s\S]*?);\s*\n\s*static boolean liquid/);
if(!m)throw new Error('GLASS_AGSL not found');
// Join the Java string pieces, dropping // comments between them.
let agsl=m[1].split('\n').map(l=>l.replace(/^\s*\/\/.*$/,'')).join('\n').match(/"((?:[^"\\]|\\.)*)"/g).map(s=>JSON.parse(s)).join('\n');
let glsl=agsl.replace(/\bhalf([234]?)\b/g,(a,n)=>n?'vec'+n:'float').replace(/\bfloat([234])\b/g,'vec$1')
  .replace('uniform shader image;','uniform sampler2D image_;uniform vec2 imageScale;')
  .replace(/image\.eval\(([^;]*?)\)\.([rgb])/g,'texture(image_,($1)*imageScale).$2')
  .replace(/vec4 main\(vec2 xy\)/,'vec4 glassMain(vec2 xy)');
const fragment='#version 300 es\nprecision highp float;\n'+glsl+'\nin vec2 v_uv;out vec4 outColor;uniform vec2 screen;\nvoid main(){vec2 xy=vec2(v_uv.x,1.0-v_uv.y)*screen;outColor=glassMain(xy);}';
const avatar='data:image/jpeg;base64,'+fs.readFileSync(path.join(root,'app/src/main/res/drawable-nodpi/author_avatar.jpg')).toString('base64');
(async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||(process.platform==='win32'?'C:/Program Files/Google/Chrome/Application/chrome.exe':undefined),headless:true,args:['--enable-unsafe-swiftshader']});
  try{
    const page=await browser.newPage();
    const res=await page.evaluate(async({fragment,avatar,light,touch,ripple})=>{
      const W=540,H=1170,d=2.6;
      // Background: a bright landscape with hard detail, so refraction is easy to judge.
      const bg=document.createElement('canvas');bg.width=W;bg.height=H;const g=bg.getContext('2d');
      let gr=g.createLinearGradient(0,0,0,H*.6);gr.addColorStop(0,'#2d5fa8');gr.addColorStop(1,'#f2b37a');g.fillStyle=gr;g.fillRect(0,0,W,H);
      g.fillStyle='#fff3c4';g.beginPath();g.arc(360,330,60,0,7);g.fill();
      for(let i=0;i<14;i++){g.fillStyle=i%2?'#1d3c57':'#e9e2d0';g.fillRect(0,560+i*44,W,22);}
      const img=new Image();img.src=avatar;await img.decode();g.drawImage(img,40,120,180,180);
      g.strokeStyle='#fff';g.lineWidth=3;for(let x=0;x<W;x+=36){g.beginPath();g.moveTo(x,0);g.lineTo(x+200,H);g.stroke();}
      const canvas=document.createElement('canvas');canvas.width=W;canvas.height=H;const gl=canvas.getContext('webgl2',{preserveDrawingBuffer:true});
      const vs='#version 300 es\nlayout(location=0) in vec2 p;out vec2 v_uv;void main(){v_uv=p*.5+.5;gl_Position=vec4(p,0,1);}';
      const prog=gl.createProgram();const errs=[];
      for(const [t,s] of [[gl.VERTEX_SHADER,vs],[gl.FRAGMENT_SHADER,fragment]]){const sh=gl.createShader(t);gl.shaderSource(sh,s);gl.compileShader(sh);if(!gl.getShaderParameter(sh,gl.COMPILE_STATUS))errs.push(gl.getShaderInfoLog(sh));gl.attachShader(prog,sh);}
      if(errs.length)return {errs,fragment};
      gl.linkProgram(prog);gl.useProgram(prog);const u=n=>gl.getUniformLocation(prog,n);
      const tex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,tex);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,bg);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
      gl.uniform1i(u('image_'),0);gl.uniform2f(u('imageScale'),1/W,1/H);gl.uniform2f(u('screen'),W,H);
      const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,2,gl.FLOAT,false,0,0);
      const sheet=document.createElement('canvas');sheet.width=W;sheet.height=H;const sc=sheet.getContext('2d');sc.drawImage(bg,0,0);
      const a=light*Math.PI/180;
      // Shapes as the app has them: a capsule button, a round button, a chip, the big sheet.
      const shapes=[[24,60,170,52,-1],[440,56,60,60,-1],[230,64,150,44,-1],[16,640,W-32,480,32*d/2.6]];
      for(const [x,y,w,h,r] of shapes){
        const rad=r<0?Math.min(w,h)/2:Math.min(r*1.15,Math.min(w,h)/2),min=Math.min(w,h);
        gl.uniform2f(u('origin'),x,y);gl.uniform2f(u('size'),w,h);gl.uniform1f(u('radius'),rad);gl.uniform1f(u('rim'),Math.max(4,Math.min(min*.42,18*d)));gl.uniform1f(u('lens'),1.07);
        gl.uniform2f(u('light'),Math.cos(a),Math.sin(a));gl.uniform1f(u('scrim'),.35);gl.uniform4f(u('tint'),1,1,1,.16*.55);
        gl.uniform3f(u('touch'),x+w*.35,y+h*.4,touch);gl.uniform2f(u('ripple'),ripple,ripple>0?1-ripple:0);gl.uniform1f(u('alpha'),1);
        gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);gl.drawArrays(gl.TRIANGLES,0,6);
        // Clip to the shape as the app's drawPath does.
        sc.save();sc.beginPath();sc.roundRect(x,y,w,h,rad);sc.clip();sc.drawImage(canvas,0,0);sc.restore();
        sc.strokeStyle='rgba(255,255,255,.35)';sc.lineWidth=1;sc.beginPath();sc.roundRect(x+.5,y+.5,w-1,h-1,rad);sc.stroke();
      }
      return {png:sheet.toDataURL('image/png')};
    },{fragment,avatar,light:opt('light',-115),touch:opt('touch',0),ripple:opt('ripple',0)});
    if(res.errs){console.error(res.errs.join('\n'));console.error(res.fragment);process.exit(1);}
    fs.writeFileSync(out,Buffer.from(res.png.split(',')[1],'base64'));console.log('wrote',out);
  }finally{await browser.close();}
})();
