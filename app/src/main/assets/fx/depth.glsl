vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Subject 3D Cutout / iOS Lock Screen 3D Parallax
    float depth = depthAt(uv);
    float fgMask = smoothstep(0.46, 0.54, depth);
    
    vec2 move = tilt * (0.018 + 0.05 * params.y) * params.x;
    vec2 fgUv = uv - move * 0.15;
    vec2 bgUv = uv + move * 1.0;
    
    vec3 fgColor = photo(fgUv, 0.0);
    vec3 bgColor = photo(bgUv, 0.0);
    
    float shadowOcclusion = smoothstep(0.48, 0.52, depthAt(uv - tilt * 0.025)) * (1.0 - fgMask);
    bgColor = mix(bgColor, bgColor * 0.55, shadowOcclusion * 0.65);
    
    return mix(bgColor, fgColor, fgMask);
}
