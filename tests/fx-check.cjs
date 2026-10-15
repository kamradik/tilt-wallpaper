// Compiles every effect in app/src/main/assets/fx exactly as GlScene assembles it (common + effect + main)
// and renders each once in WebGL2 at a tilt, failing on compile/link errors or an empty frame.
// Usage: PLAYWRIGHT_MODULE=<path to playwright> node tests/fx-check.cjs [chrome.exe]
// Off Windows (CI) it uses Playwright's bundled Chromium unless CHROME_PATH or the argument names a browser.
const fs=require('fs');
const path=require('path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const dir=path.join(__dirname,'..','app/src/main/assets/fx');
const quad=fs.readFileSync(path.join(__dirname,'..','app/src/main/assets/quad.glsl'),'utf8');
const common=fs.readFileSync(path.join(dir,'common.glsl'),'utf8'),main=fs.readFileSync(path.join(dir,'main.glsl'),'utf8');
const effects=fs.readdirSync(dir).filter(f=>f.endsWith('.glsl')&&f!=='common.glsl'&&f!=='main.glsl').map(f=>[f.replace('.glsl',''),common+'\n'+fs.readFileSync(path.join(dir,f),'utf8')+'\n'+main]);
(async()=>{
  const browser=await chromium.launch({executablePath:process.argv[2]||process.env.CHROME_PATH||(process.platform==='win32'?'C:/Program Files/Google/Chrome/Application/chrome.exe':undefined),headless:true,args:['--enable-unsafe-swiftshader']});
  try{
    const page=await browser.newPage();
    const results=await page.evaluate(({quad,effects})=>{
      const canvas=document.createElement('canvas');canvas.width=180;canvas.height=390;
      const gl=canvas.getContext('webgl2');if(!gl)throw Error('No WebGL2');
      const tex=(w,h,fill)=>{const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);const px=new Uint8Array(w*h*4);for(let i=0;i<w*h;i++){const v=fill(i%w/w,Math.floor(i/w)/h);px.set(v,i*4);}gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,px);gl.generateMipmap(gl.TEXTURE_2D);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);return t;};
      const image=tex(256,512,(x,y)=>[255*x,120+100*Math.sin(y*12),255*y,255]),depth=tex(64,128,(x,y)=>{const d=Math.max(0,1-Math.hypot(x-.5,y-.5)*2.2)*255;return[d,d,d,255];}),wipe=tex(64,128,()=>[0,0,0,255]);
      const vao=gl.createVertexArray();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,gl.createBuffer());
      gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,0,0,1,-1,1,0,-1,1,0,1,-1,1,0,1,1,-1,1,0,1,1,1,1]),gl.STATIC_DRAW);
      gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,2,gl.FLOAT,false,16,0);gl.enableVertexAttribArray(1);gl.vertexAttribPointer(1,2,gl.FLOAT,false,16,8);
      const out=[];
      for(const [name,source] of effects){
        const p=gl.createProgram();
        for(const [type,src] of [[gl.VERTEX_SHADER,quad],[gl.FRAGMENT_SHADER,source]]){const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)){out.push({name,error:gl.getShaderInfoLog(s)});continue;}gl.attachShader(p,s);}
        if(out.length&&out[out.length-1].name===name)continue;
        gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS)){out.push({name,error:gl.getProgramInfoLog(p)});continue;}
        gl.useProgram(p);const u=n=>gl.getUniformLocation(p,n);
        [[gl.TEXTURE0,image,'u_image',0],[gl.TEXTURE1,depth,'u_depth',1],[gl.TEXTURE2,wipe,'u_wipeMask',2]].forEach(([unit,t,n,i])=>{gl.activeTexture(unit);gl.bindTexture(gl.TEXTURE_2D,t);gl.uniform1i(u(n),i);});
        const aspect=180/390,mm=70,x=30*.75,r=x*Math.PI/180,s=Math.sin(r),c=Math.cos(r);
        gl.uniform2f(u('u_imageSize'),256,512);gl.uniform2f(u('u_coverScale'),1,1);gl.uniform2f(u('u_crop'),.5,.5);gl.uniform1f(u('u_zoom'),1.08);gl.uniform1f(u('u_aspect'),aspect);gl.uniform1f(u('u_screenWidthMm'),mm);
        gl.uniform3f(u('u_projectionX'),c,0,.5*(c-1));gl.uniform3f(u('u_projectionY'),0,1,0);gl.uniform3f(u('u_projectionZ'),-s*mm/320,0,1-s*mm*.5/320);gl.uniform3f(u('u_gap'),s*mm,0,s*mm*.5);gl.uniform3f(u('u_blurGap'),20,0,10);
        gl.uniform2f(u('u_tilt'),.45,-.2);gl.uniform4f(u('u_touch'),.5,.5,.3,1);gl.uniform2f(u('u_params'),.7,.55);gl.uniform1f(u('u_toggle'),1);gl.uniform1f(u('u_motion'),1);gl.uniform1f(u('u_time'),3.2);gl.uniform2f(u('u_wake'),.4,1);gl.uniform1f(u('u_alpha'),1);
        gl.viewport(0,0,180,390);gl.clearColor(0,0,0,1);gl.clear(gl.COLOR_BUFFER_BIT);gl.drawArrays(gl.TRIANGLES,0,6);
        const px=new Uint8Array(180*390*4);gl.readPixels(0,0,180,390,gl.RGBA,gl.UNSIGNED_BYTE,px);let lit=0;for(let i=0;i<px.length;i+=4)if(px[i]+px[i+1]+px[i+2]>30)lit++;
        out.push({name,lit,error:gl.getError()?'GL error '+gl.getError():lit<5000?'empty frame':null});
      }
      return out;
    },{quad,effects});
    const failed=results.filter(r=>r.error);
    for(const r of results)console.log((r.error?'FAIL ':'ok   ')+r.name+(r.error?' — '+r.error.trim():' lit='+r.lit));
    console.log(`${results.length-failed.length}/${results.length} effects compiled and rendered`);
    if(failed.length)process.exitCode=1;
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
