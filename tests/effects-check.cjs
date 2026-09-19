const fs=require('fs');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--enable-unsafe-swiftshader']});
 try{
 const page=await browser.newPage({viewport:{width:1400,height:690},deviceScaleFactor:1});
 await page.setContent('<body style="background:#16171b;color:#eee;font:15px sans-serif;margin:20px"><h2>Solo · GPU effect checks</h2><main style="display:flex;gap:12px"></main></body>');
 const shader={};for(const n of ['quad','effects','blur'])shader[n]=fs.readFileSync('app/src/main/assets/'+n+'.glsl','utf8');
 const result=await page.evaluate(shader=>{
   let results=[];
   for(let mode=0;mode<5;mode++){
    let card=document.createElement('div'),caption=document.createElement('p');caption.textContent=['Classic Tilt','Depth Parallax','Dynamic Light','Liquid Glass','Focus Rack'][mode];card.append(caption);document.querySelector('main').append(card);
    let canvas=document.createElement('canvas');canvas.width=256;canvas.height=554;card.append(canvas);let g=canvas.getContext('webgl2',{preserveDrawingBuffer:true});if(!g)throw Error('WebGL2 unavailable');
    let make=f=>{let p=g.createProgram();for(let [type,source] of [[g.VERTEX_SHADER,shader.quad],[g.FRAGMENT_SHADER,f]]){let s=g.createShader(type);g.shaderSource(s,source);g.compileShader(s);if(!g.getShaderParameter(s,g.COMPILE_STATUS))throw Error(g.getShaderInfoLog(s));g.attachShader(p,s);}g.linkProgram(p);if(!g.getProgramParameter(p,g.LINK_STATUS))throw Error(g.getProgramInfoLog(p));return p;};
    let p=make(shader.effects),blur=make(shader.blur);let vao=g.createVertexArray();g.bindVertexArray(vao);g.bindBuffer(g.ARRAY_BUFFER,g.createBuffer());g.bufferData(g.ARRAY_BUFFER,new Float32Array([-1,-1,0,0,1,-1,1,0,-1,1,0,1,-1,1,0,1,1,-1,1,0,1,1,1,1]),g.STATIC_DRAW);for(let i=0;i<2;i++){g.enableVertexAttribArray(i);g.vertexAttribPointer(i,2,g.FLOAT,false,16,i*8);}
    let img=document.createElement('canvas');img.width=512;img.height=1108;let ctx=img.getContext('2d'),grad=ctx.createLinearGradient(0,0,512,1108);grad.addColorStop(0,'#153b42');grad.addColorStop(.5,'#a7baba');grad.addColorStop(1,'#282735');ctx.fillStyle=grad;ctx.fillRect(0,0,512,1108);
    for(let i=0;i<11;i++){ctx.fillStyle=i%2?'#c8d2c2':'#6a8b85';ctx.beginPath();ctx.moveTo(i*52,1108);ctx.bezierCurveTo(480-i*22,640,-100+i*55,330,i*60,0);ctx.lineTo(i*60+22,0);ctx.bezierCurveTo(-90+i*55,330,500-i*22,650,i*52+32,1108);ctx.fill();}
    ctx.fillStyle='#f8a56b';ctx.beginPath();ctx.ellipse(240,570,130,180,0,0,Math.PI*2);ctx.fill();ctx.fillStyle='#fff1d9';ctx.font='bold 68px sans-serif';ctx.fillText('SOLO',140,575);
    let texture=(unit,source,mip)=>{g.activeTexture(g.TEXTURE0+unit);let t=g.createTexture();g.bindTexture(g.TEXTURE_2D,t);g.texImage2D(g.TEXTURE_2D,0,g.RGBA,g.RGBA,g.UNSIGNED_BYTE,source);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_MIN_FILTER,mip?g.LINEAR_MIPMAP_LINEAR:g.LINEAR);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_MAG_FILTER,g.LINEAR);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_WRAP_S,g.CLAMP_TO_EDGE);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_WRAP_T,g.CLAMP_TO_EDGE);if(mip)g.generateMipmap(g.TEXTURE_2D);return t;};
    let photo=texture(0,img,true),levels=Math.floor(Math.log2(1108)),fbo=g.createFramebuffer(),temp=g.createTexture();g.bindTexture(g.TEXTURE_2D,temp);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_MIN_FILTER,g.LINEAR);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_MAG_FILTER,g.LINEAR);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_WRAP_S,g.CLAMP_TO_EDGE);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_WRAP_T,g.CLAMP_TO_EDGE);
    for(let level=1;level<=levels;level++){
      let w=Math.max(1,512>>level),h=Math.max(1,1108>>level);g.bindFramebuffer(g.FRAMEBUFFER,fbo);g.bindTexture(g.TEXTURE_2D,temp);g.texImage2D(g.TEXTURE_2D,0,g.RGBA,w,h,0,g.RGBA,g.UNSIGNED_BYTE,null);g.framebufferTexture2D(g.FRAMEBUFFER,g.COLOR_ATTACHMENT0,g.TEXTURE_2D,temp,0);if(g.checkFramebufferStatus(g.FRAMEBUFFER)!==g.FRAMEBUFFER_COMPLETE)throw Error('Blur FBO');
      g.viewport(0,0,w,h);g.useProgram(blur);g.uniform1i(g.getUniformLocation(blur,'u_source'),0);g.bindTexture(g.TEXTURE_2D,photo);g.uniform1f(g.getUniformLocation(blur,'u_level'),level-1);g.uniform2f(g.getUniformLocation(blur,'u_step'),1/Math.max(1,512>>(level-1)),0);g.drawArrays(g.TRIANGLES,0,6);
      g.framebufferTexture2D(g.FRAMEBUFFER,g.COLOR_ATTACHMENT0,g.TEXTURE_2D,photo,level);g.bindTexture(g.TEXTURE_2D,temp);g.uniform1f(g.getUniformLocation(blur,'u_level'),0);g.uniform2f(g.getUniformLocation(blur,'u_step'),0,1/Math.max(1,1108>>(level-1)));g.drawArrays(g.TRIANGLES,0,6);if(g.getError())throw Error('Gaussian pass '+level);
    }
    let depth=document.createElement('canvas');depth.width=512;depth.height=1108;let d=depth.getContext('2d');d.fillStyle='#222';d.fillRect(0,0,512,1108);d.fillStyle='#eee';d.beginPath();d.ellipse(240,570,130,180,0,0,Math.PI*2);d.fill();texture(1,depth,false);
    g.bindFramebuffer(g.FRAMEBUFFER,null);g.useProgram(p);g.activeTexture(g.TEXTURE0);g.bindTexture(g.TEXTURE_2D,photo);g.viewport(0,0,256,554);let u=n=>g.getUniformLocation(p,n);
    g.uniform1i(u('u_image'),0);g.uniform1i(u('u_depth'),1);g.uniform2f(u('u_imageSize'),512,1108);g.uniform2f(u('u_coverScale'),1,1);g.uniform2f(u('u_crop'),.5,.5);g.uniform1f(u('u_zoom'),1.08);g.uniform1f(u('u_aspect'),256/554);g.uniform1f(u('u_screenWidthMm'),72);g.uniform1f(u('u_motion'),1);g.uniform2f(u('u_tilt'),.6,.2);g.uniform4f(u('u_touch'),.45,.5,.1,1);g.uniform2f(u('u_params'),.65,.6);g.uniform2f(u('u_previousParams'),.5,.5);g.uniform1i(u('u_previous'),0);g.uniform1i(u('u_effect'),mode);g.uniform1f(u('u_transition'),1);
    let theta=.4,c=Math.cos(theta),s=Math.sin(theta);g.uniform3f(u('u_projectionX'),c,0,.5*(c-1));g.uniform3f(u('u_projectionY'),0,1,0);g.uniform3f(u('u_projectionZ'),-s*72/320,0,1-s*36/320);g.uniform3f(u('u_gap'),s*72,0,s*36);g.uniform3f(u('u_blurGap'),15,1,8);
    let pixels=new Uint8Array(256*554*4),energies=[];for(let tilt of [-.65,0,.65]){g.uniform2f(u('u_tilt'),tilt,.2);g.drawArrays(g.TRIANGLES,0,6);g.readPixels(0,0,256,554,g.RGBA,g.UNSIGNED_BYTE,pixels);let total=0;for(let i=0;i<pixels.length;i+=4)total+=pixels[i]+pixels[i+1]+pixels[i+2];if(total<100000)throw Error('Black output '+mode);energies.push(total);}
    if(mode>0&&Math.max(...energies)-Math.min(...energies)<10)throw Error('Not interactive '+mode);
    // No holes on the output perimeter, even at maximum displacement/crop.
    let blackEdge=0;for(let x=0;x<256;x++){for(let y of [0,553]){let i=(y*256+x)*4;if(pixels[i]+pixels[i+1]+pixels[i+2]<5)blackEdge++;}}if(blackEdge)throw Error('Empty edge '+mode);
    g.uniform1f(u('u_transition'),.5);g.drawArrays(g.TRIANGLES,0,6);if(g.getError())throw Error('Transition GL error');g.uniform1f(u('u_transition'),1);g.drawArrays(g.TRIANGLES,0,6);
    results.push({effect:caption.textContent,gaussianLevels:levels,energy:energies,emptyEdgePixels:blackEdge});
   }return results;
 },shader);
 fs.mkdirSync('analysis',{recursive:true});await page.screenshot({path:'analysis/effects-check.png'});fs.writeFileSync('analysis/effects-check.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
