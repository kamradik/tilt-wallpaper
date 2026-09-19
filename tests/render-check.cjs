const fs=require('fs');
const path=require('path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE);
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--enable-unsafe-swiftshader']});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:850},deviceScaleFactor:1});
  await page.setContent('<html><body style="margin:0;background:#15131a;color:white;font:16px sans-serif"><h2 style="margin:20px">Проверка оптической модели · 0°, 35°, 70°, −70°</h2><main style="display:flex;gap:16px;padding:16px"></main></body></html>');
  const shaders=Object.fromEntries(['quad','fold','blur'].map(n=>[n,fs.readFileSync(path.join('app/src/main/assets',n+'.glsl'),'utf8')]));
  const result=await page.evaluate(({shaders})=>{
   function check(gl,label){let e=gl.getError();if(e)throw Error(label+': GL '+e);}
   let results=[];
   for(let angle of [0,35,70,-70]){
    let canvas=document.createElement('canvas');canvas.width=336;canvas.height=728;document.querySelector('main').append(canvas);
    const gl=canvas.getContext('webgl2',{preserveDrawingBuffer:true});if(!gl)throw Error('No WebGL2');
    function program(fragment){let p=gl.createProgram();for(let [type,src] of [[gl.VERTEX_SHADER,shaders.quad],[gl.FRAGMENT_SHADER,fragment]]){let s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));gl.attachShader(p,s);}gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;}
    const p=program(shaders.fold);program(shaders.blur);gl.useProgram(p);
    const vao=gl.createVertexArray();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,gl.createBuffer());gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,0,0,1,-1,1,0,-1,1,0,1,-1,1,0,1,1,-1,1,0,1,1,1,1]),gl.STATIC_DRAW);gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,2,gl.FLOAT,false,16,0);gl.enableVertexAttribArray(1);gl.vertexAttribPointer(1,2,gl.FLOAT,false,16,8);
    let image=document.createElement('canvas');image.width=672;image.height=1456;let ctx=image.getContext('2d'),gradient=ctx.createLinearGradient(0,0,672,1456);gradient.addColorStop(0,'#19184d');gradient.addColorStop(.5,'#fd6099');gradient.addColorStop(1,'#ffd19c');ctx.fillStyle=gradient;ctx.fillRect(0,0,672,1456);
    ctx.strokeStyle='white';ctx.lineWidth=4;for(let y=60;y<1456;y+=80){ctx.beginPath();ctx.moveTo(0,y);ctx.bezierCurveTo(500,y-190,200,y+190,672,y);ctx.stroke();}ctx.fillStyle='white';ctx.font='bold 80px sans-serif';ctx.fillText('TILT',45,740);
    let tex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,tex);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);gl.generateMipmap(gl.TEXTURE_2D);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
    // Browser harness checks the exact shaders and projection. Android's Gaussian mip pipeline is separate.
    let u=n=>gl.getUniformLocation(p,n),aspect=336/728,mm=72,x=angle*.75,r=Math.abs(x)*Math.PI/180,s=Math.sin(r),c=Math.cos(r),sign=x<0?-1:1,blur=.68*Math.sin(Math.PI/2*Math.max(0,Math.min(1,(Math.abs(x)-14)/86)))*mm;
    gl.uniform1i(u('u_image'),0);gl.uniform2f(u('u_imageSize'),672,1456);gl.uniform2f(u('u_coverScale'),1,1);gl.uniform1f(u('u_aspect'),aspect);gl.uniform1f(u('u_screenWidthMm'),mm);
    gl.uniform3f(u('u_projectionX'),c,0,sign*.5*(c-1));gl.uniform3f(u('u_projectionY'),0,1,0);gl.uniform3f(u('u_projectionZ'),-sign*s*mm/320,0,1-s*mm*.5/320);gl.uniform3f(u('u_gap'),sign*s*mm,0,s*mm*.5);gl.uniform3f(u('u_blurGap'),sign*blur,0,blur*.5);
    gl.viewport(0,0,336,728);gl.drawArrays(gl.TRIANGLES,0,6);check(gl,'render');let pixels=new Uint8Array(336*728*4);gl.readPixels(0,0,336,728,gl.RGBA,gl.UNSIGNED_BYTE,pixels);let lit=0,energy=0;for(let i=0;i<pixels.length;i+=4){let val=pixels[i]+pixels[i+1]+pixels[i+2];energy+=val;if(val>30)lit++;}if(lit<1000)throw Error('Empty render');results.push({angle,lit,energy});
   }
   if(results[0].energy<=results[2].energy)throw Error('Expected optical dimming');return results;
  },{shaders});
  await page.screenshot({path:'analysis/shader-check.png'});fs.writeFileSync('analysis/shader-check.json',JSON.stringify({status:'passed',scope:'WebGL shader compilation and projection; standard mipmaps, not Android lifecycle or Gaussian pipeline',results:result},null,2));console.log(JSON.stringify(result));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
