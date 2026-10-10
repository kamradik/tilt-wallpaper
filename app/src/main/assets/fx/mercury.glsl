vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Liquid Mercury: Viscous molten chrome mirror ripples flowing over highlight contours
    vec3 base = photo(uv, 0.0);
    float luma = dot(base, vec3(0.299, 0.587, 0.114));
    float mercuryMask = smoothstep(0.38, 0.85, luma);
    
    // Viscous molten chrome ripple flow
    float rippleToggle = (u_toggle > 0.5) ? 1.0 : 0.0;
    vec2 waveCoord = uv * 16.0 + tilt * 3.5;
    float ripple1 = sin(waveCoord.x * 1.8 + u_time * 2.5 + sin(waveCoord.y * 1.4));
    float ripple2 = cos(waveCoord.y * 1.8 - u_time * 2.2 + cos(waveCoord.x * 1.4));
    vec2 rippleNorm = vec2(ripple1, ripple2) * 0.028 * params.x * mercuryMask * rippleToggle;
    
    // Chrome environment reflection & Fresnel curve
    vec3 chromeReflect = photo(uv + rippleNorm * 2.4 - tilt * 0.015, 0.0);
    float fresnel = pow(1.0 - abs(dot(normalize(vec3(rippleNorm, 0.5)), vec3(0.0, 0.0, 1.0))), 3.0);
    vec3 chrome = mix(chromeReflect * vec3(1.15, 1.18, 1.25), vec3(1.0), fresnel * 0.65);
    
    return mix(base, chrome, mercuryMask * params.y);
}
