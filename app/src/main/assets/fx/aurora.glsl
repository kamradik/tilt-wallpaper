vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Aurora Borealis: Ionized atmospheric curtains waving with tilt and time
    vec3 base = photo(uv - tilt * 0.010, 0.0);
    
    // Multi-frequency undulating auroral ribbon curves
    vec2 auUv = uv + vec2(tilt.x * 0.35, -tilt.y * 0.20);
    float wave1 = sin(auUv.x * 4.5 + u_time * 1.2 + sin(auUv.x * 2.0));
    float wave2 = cos(auUv.x * 7.0 - u_time * 1.5 + cos(auUv.y * 3.0));
    float curtainY = 0.35 + wave1 * 0.12 + wave2 * 0.06;
    
    // Vertical curtain ray falloff
    float distToCurtain = (auUv.y - curtainY);
    float curtainMask = smoothstep(0.45, 0.0, distToCurtain) * smoothstep(-0.20, 0.05, distToCurtain);
    
    // Filament striations along vertical lines
    float striation = sin(auUv.x * 45.0 + wave1 * 6.0) * 0.5 + 0.5;
    striation = pow(striation, 2.5);
    
    // Spectral emission colors: ionized oxygen green to nitrogen violet
    vec3 auroraGreen = vec3(0.12, 0.98, 0.55);
    vec3 auroraViolet = vec3(0.70, 0.15, 0.95);
    vec3 auroraColor = mix(auroraGreen, auroraViolet, smoothstep(-0.05, 0.35, distToCurtain));
    
    float auroraIntensity = curtainMask * (0.6 + 0.4 * striation) * (1.8 * params.x);
    vec3 composite = base + auroraColor * auroraIntensity * mix(0.7, 1.2, params.y);
    return composite;
}
