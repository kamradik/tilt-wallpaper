vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Lenticular 3D: Cylindrical lenticular autostereoscopic raster slicing with Subject 3D Cutout
    float lenticularPitch = mix(140.0, 320.0, params.y);
    float lensUv = fract(uv.x * lenticularPitch);
    float lensNorm = (lensUv - 0.5) * 2.0;
    
    float tiltPhase = tilt.x * 4.5 + lensNorm * 0.70;
    float d = depthAt(uv);
    float fgMask = smoothstep(0.46, 0.54, d);
    
    // Micro-parallax for foreground (0.15x), full stereo depth for background (1.0x)
    float parallaxScale = mix(1.0, 0.15, fgMask);
    float parallaxShift = 0.024 * (d - 0.5) * params.x * parallaxScale;
    vec2 leftPhase = uv + vec2(-parallaxShift + tilt.x * 0.008 * parallaxScale, 0.0);
    vec2 rightPhase = uv + vec2(parallaxShift + tilt.x * 0.008 * parallaxScale, 0.0);
    
    float blend = smoothstep(-0.45, 0.45, sin(tiltPhase));
    vec3 stereo = mix(photo(leftPhase, 0.0), photo(rightPhase, 0.0), blend);
    
    // Contact shadow on background behind cutout subject
    float shadowOcclusion = smoothstep(0.48, 0.52, depthAt(uv - tilt * 0.025)) * (1.0 - fgMask);
    stereo = mix(stereo, stereo * 0.55, shadowOcclusion * 0.65);
    
    // Cylindrical lenticular optical rib reflection
    float ribToggle = (u_toggle > 0.5) ? 1.0 : 0.0;
    float ribSpecular = pow(max(0.0, 1.0 - abs(lensNorm)), 8.0) * 0.18 * params.x * ribToggle;
    return stereo + vec3(ribSpecular);
}
