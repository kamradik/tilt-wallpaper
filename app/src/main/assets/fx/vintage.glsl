vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Vintage 1970s Cine Lens: Authentic Kodachrome color tone, optical barrel distortion, anamorphic flare & analog grain
    vec2 centerUv = uv - 0.5;
    float r2 = dot(centerUv, centerUv);
    
    // 1. Prime lens optical breathing & barrel distortion
    float breath = (sin(u_time * 1.2) * 0.008 + exp(-u_wake.x * 3.5) * 0.02) * params.x;
    float barrel = 1.0 + r2 * (0.16 * params.x) + breath;
    vec2 warpedUv = 0.5 + centerUv * barrel;
    
    // 2. Transverse chromatic aberration (red-cyan fringing toward the outer frame)
    vec2 redDisp = centerUv * (r2 * 0.022 * params.x);
    vec2 blueDisp = -redDisp * 0.75;
    
    vec3 vintage;
    vintage.r = photo(warpedUv + redDisp, 0.0).r;
    vintage.g = photo(warpedUv, 0.0).g;
    vintage.b = photo(warpedUv + blueDisp, 0.0).b;
    
    // 3. Warm 1970s Kodachrome / Ektachrome color grading
    vintage = pow(max(vec3(0.0), vintage), vec3(0.92, 0.98, 1.06));
    vintage *= vec3(1.08, 1.01, 0.89);
    vintage += vec3(0.022, 0.014, 0.006) * (1.0 - vintage.g);
    
    // 4. Subtle anamorphic edge streak flare (moves with device tilt, NOT a center circle!)
    float tiltShift = tilt.x * 0.4 - tilt.y * 0.2;
    float streakDist = abs(dot(centerUv, vec2(0.25, 0.97)) - tiltShift * 0.35);
    float streak = exp(-streakDist * streakDist * 180.0) * 0.45 * params.y;
    vec3 streakTint = mix(vec3(1.2, 0.85, 0.45), vec3(0.5, 0.9, 1.2), 0.5 + 0.5 * tilt.x);
    vintage += streakTint * streak;
    
    // Highlight optical bloom
    vec3 bloom = photo(warpedUv, 2.4);
    vintage += bloom * vec3(1.15, 0.70, 0.30) * 0.22 * params.x;
    
    // 5. Fine 16mm organic analog film grain
    float grainNoise = fract(sin(dot(uv * 380.0, vec2(12.9898, 78.233)) + u_time * 9.0) * 43758.5453);
    float grain = (grainNoise - 0.5) * 0.042 * params.y;
    vintage += vec3(grain);
    
    // 6. Smooth vintage corner vignette falloff
    float vignette = smoothstep(0.92, 0.40, length(centerUv * vec2(1.0, u_aspect)));
    vintage *= mix(0.72, 1.0, vignette);
    
    return vintage;
}
