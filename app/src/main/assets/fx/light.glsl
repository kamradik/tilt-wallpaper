vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Dynamic Light: Expanded Blinn-Phong brightness, soft film knee, color temperature & beam direction
    vec3 base=photo(uv,0.0);
    
    // Color temperature from params.y: warm gold (0.0) -> diamond white (0.5) -> sapphire moon (1.0)
    vec3 warmGold=vec3(1.05,0.82,0.46);
    vec3 diamondWhite=vec3(1.0,1.0,1.0);
    vec3 sapphireMoon=vec3(0.55,0.80,1.20);
    vec3 lightColor=params.y<0.5?mix(warmGold,diamondWhite,params.y*2.0):mix(diamondWhite,sapphireMoon,(params.y-0.5)*2.0);
    
    // Beam direction governed by params.y and tilt
    float beamAngle=(params.y-0.5)*2.8-0.6+tilt.x*0.45;
    vec2 beamAxis=vec2(cos(beamAngle),sin(beamAngle));
    vec2 beamPerp=vec2(-beamAxis.y,beamAxis.x);
    
    vec2 center=vec2(0.5,0.5)+tilt*0.35+beamPerp*0.12;
    vec2 p=(uv-center)*vec2(1.0,1.0/u_aspect);
    float d=length(p);
    
    // Corona halo around light core
    float coreGlow=exp(-d*d*35.0)*1.8;
    float corona=exp(-d*3.2)*0.65+exp(-d*7.5)*0.85;
    
    // Sweeping anamorphic beam flare
    float narrow=dot(p,beamAxis);
    float longit=dot(p,beamPerp);
    float beamWidth=mix(0.14,0.38,0.3+0.4*sin(params.y*3.14159));
    float beamFlare=exp(-narrow*narrow/(beamWidth*beamWidth)-longit*longit/3.0)*1.2;
    
    // Micro-surface normals from image luminance for Blinn-Phong specular glint
    vec2 texel=2.0/u_imageSize;
    float luma=dot(base,vec3(0.299,0.587,0.114));
    float lumaL=dot(photo(uv-vec2(texel.x,0.0),0.0),vec3(0.299,0.587,0.114));
    float lumaR=dot(photo(uv+vec2(texel.x,0.0),0.0),vec3(0.299,0.587,0.114));
    float lumaD=dot(photo(uv-vec2(0.0,texel.y),0.0),vec3(0.299,0.587,0.114));
    float lumaU=dot(photo(uv+vec2(0.0,texel.y),0.0),vec3(0.299,0.587,0.114));
    vec3 N=normalize(vec3((lumaL-lumaR)*3.0,(lumaD-lumaU)*3.0,1.0));
    vec3 L=normalize(vec3(tilt.x*2.2-beamAxis.x*0.8,-tilt.y*2.2-beamAxis.y*0.8,1.0));
    vec3 V=vec3(0.0,0.0,1.0);
    vec3 H=normalize(L+V);
    float ndoth=max(0.0,dot(N,H));
    float spec=pow(ndoth,36.0)*(0.35+0.65*luma);
    
    // Soft film knee tonemapping for rich highlights
    float beamIntensity=(corona*0.5+beamFlare*0.65+spec*1.8)*params.x;
    float coreIntensity=coreGlow*params.x*0.75;
    vec3 lightBloom=lightColor*(beamIntensity+coreIntensity);
    vec3 tonemappedLight=lightBloom/(vec3(1.0)+lightBloom*0.55);
    
    vec3 lit=1.0-(1.0-base)*(1.0-clamp(tonemappedLight*0.8,0.0,0.92));
    lit+=lightColor*(coreGlow*0.35+spec*0.5)*params.x;
    // Sharpness boost toggle (ON by default)
    if(u_toggle > 0.5){
        vec3 blurSample = photo(uv, 1.2);
        vec3 highPass = lit - blurSample;
        lit = clamp(lit + highPass * (0.35 + 0.35 * params.x), 0.0, 1.0);
    }
    return lit;
}
