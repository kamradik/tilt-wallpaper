vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // CineStill 800T / Anamorphic Cine: Optical halation, horizontal anamorphic streak flare & film S-curve
    vec3 base=photo(uv,0.0);
    float wake=u_wake.x;
    float shutterFlash=exp(-wake*7.5)*1.2;
    vec3 exposed=base*(1.0+shutterFlash);
    float luma=dot(exposed,vec3(0.2126,0.7152,0.0722));
    
    // Optical Halation (amber-red bloom on highlights luma > 0.65)
    vec3 blurred=photo(uv,2.5);
    float blurredLuma=dot(blurred,vec3(0.2126,0.7152,0.0722));
    float halationMask=smoothstep(0.60,0.92,blurredLuma)*(1.0-smoothstep(0.92,1.0,luma)*0.4);
    vec3 halationColor=vec3(1.0,0.22,0.04);
    vec3 halation=halationColor*halationMask*params.x*1.1;
    
    // Horizontal Anamorphic Streak Flare
    vec2 streakStep=vec2(7.0/u_imageSize.x,0.0);
    vec3 streakSample=photo(uv-streakStep*3.5,1.6)*0.15
                     +photo(uv-streakStep*1.8,1.0)*0.25
                     +photo(uv,0.5)*0.35
                     +photo(uv+streakStep*1.8,1.0)*0.25
                     +photo(uv+streakStep*3.5,1.6)*0.15;
    float streakLuma=dot(streakSample,vec3(0.2126,0.7152,0.0722));
    float streakMask=smoothstep(0.62,0.95,streakLuma);
    vec3 flareCyan=vec3(0.12,0.58,1.10);
    vec3 flareGold=vec3(1.10,0.75,0.32);
    vec3 flareTint=mix(flareCyan,flareGold,params.y);
    float tiltFactor=(u_toggle > 0.5) ? abs(tilt.x) : 0.25;
    vec3 anamorphicStreak=flareTint*streakMask*(0.8+0.6*tiltFactor)*params.x*0.85;
    
    // Cinematic S-curve tonemapping & lifted shadows
    vec3 lifted=exposed+vec3(0.018,0.024,0.032)*params.x;
    vec3 filmToe=smoothstep(0.0,1.0,lifted);
    vec3 film=mix(exposed,filmToe,0.65+0.35*params.x);
    film*=vec3(1.03,0.99,0.94);
    
    // Organic film grain in midtones
    float midtoneWeight=4.0*luma*(1.0-luma);
    float noise=fract(sin(dot(uv*u_imageSize+vec2(u_time*35.0,u_time*17.0),vec2(12.9898,78.233)))*43758.5453);
    float grain=(noise-0.5)*(0.04+0.06*params.x)*midtoneWeight;
    
    return film+halation+anamorphicStreak+vec3(grain);
}
