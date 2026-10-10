vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Condensation Glass: Procedural droplets, organic condensation edge nucleation and progressive optical diffusion
    float blurLod=mix(1.8,5.0,params.x);
    vec3 steamed=photo(uv,blurLod);
    
    float dropScale=mix(36.0,88.0,params.y);
    vec2 gv=uv*dropScale*vec2(1.0,1.0/u_aspect);
    vec2 cellId=floor(gv);
    vec2 cellUv=fract(gv)-0.5;
    vec2 rnd=hash2(cellId);
    vec2 dropPos=(rnd-0.5)*0.55;
    float dropR=rnd.x*0.32+0.12;
    float dropDist=length(cellUv-dropPos);
    
    float dropZ=sqrt(max(0.0,1.0-(dropDist/dropR)*(dropDist/dropR)));
    vec2 dropNorm=(cellUv-dropPos)/dropR;
    vec3 dropL=normalize(vec3(tilt.x*1.5,-tilt.y*1.5+0.4,0.9));
    float dropSpec=pow(max(0.0,dot(vec3(dropNorm*dropZ,dropZ),dropL)),22.0)*smoothstep(dropR,dropR*0.75,dropDist);
    vec3 frosted=steamed*(0.96+0.04*rnd.y)+vec3(dropSpec*0.6*params.x);
    
    // Full-screen continuous wipe mask with organic condensation healing
    float rawClear=texture(u_wipeMask,coords(uv)).r;
    
    // Micro-organic condensation texture pattern
    vec2 frostCoord=uv*60.0;
    float f1=sin(frostCoord.x*2.8+sin(frostCoord.y*3.4));
    float f2=cos(frostCoord.y*2.8+sin(frostCoord.x*3.4));
    float noisePattern=(f1*f2)*0.5+0.5;
    float frostNoise=fract(sin(dot(uv*240.0,vec2(12.9898,78.233)))*43758.5453);
    
    // Organic edge nucleation: moisture condenses back from perimeter inward
    float condensationEdge=smoothstep(0.05,0.95,rawClear+(noisePattern-0.5)*(1.0-rawClear)*0.45);
    
    // Progressive optical diffusion (image diffuses softly back from sharp to frosted)
    float dynamicLod=blurLod*(1.0-smoothstep(0.05,0.92,condensationEdge));
    vec3 dynamicPhoto=photo(uv,dynamicLod);
    
    // Sparkling micro dew beads during condensation recovery
    float dewBeads=pow(frostNoise,14.0)*smoothstep(0.10,0.65,1.0-rawClear)*smoothstep(0.05,0.75,rawClear);
    vec3 dewSparkle=vec3(0.90,0.96,1.0)*dewBeads*2.2*params.x;
    
    // Surface tension meniscus highlight along the clearing border
    float meniscus=smoothstep(0.06,0.32,condensationEdge)*smoothstep(0.88,0.32,condensationEdge);
    vec3 meniscusColor=vec3(0.86,0.94,1.0)*meniscus*0.48*params.x;
    
    vec3 result=mix(frosted,dynamicPhoto,condensationEdge)+dewSparkle+meniscusColor;
    return result;
}
