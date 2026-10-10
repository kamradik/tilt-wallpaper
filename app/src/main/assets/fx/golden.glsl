vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Golden Hour Flare: Sunset amber backlighting, anamorphic flare, and dreamy bloom
    vec2 sunPos = vec2(0.5 + tilt.x * 0.6, 0.25 - tilt.y * 0.3);
    vec2 dSun = uv - sunPos;
    float sunDist = length(dSun * vec2(1.0, u_aspect));
    
    // Anamorphic horizontal optical streak
    float streak = exp(-abs(dSun.y) * mix(80.0, 240.0, 1.0 - params.y)) * exp(-abs(dSun.x) * 1.5) * 0.85 * params.x;
    
    // Warm golden hour grading on base photo
    vec3 base = photo(uv - tilt * 0.006, 0.0);
    vec3 warmTone = pow(max(vec3(0.0), base), vec3(0.92, 0.96, 1.08)) * vec3(1.18, 1.02, 0.82);
    
    // Highlights optical bloom
    vec3 bloom = photo(uv, 2.6);
    float bloomWeight = smoothstep(0.40, 0.85, dot(bloom, vec3(0.299, 0.587, 0.114))) * 0.45 * params.x;
    
    // Radial sunset glare halo
    float sunGlow = exp(-sunDist * 2.8) * 0.65 * params.x;
    vec3 goldenSun = vec3(1.25, 0.78, 0.32);
    
    vec3 finalColor = mix(base, warmTone, 0.65) + bloom * goldenSun * bloomWeight + goldenSun * sunGlow + vec3(1.1, 0.85, 0.4) * streak;
    return finalColor;
}
