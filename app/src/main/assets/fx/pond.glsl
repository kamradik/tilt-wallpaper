vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Zen Pond Waves: Concentric pond ripples and traveling waves with refraction caustics
    vec2 p = (uv - 0.5) * vec2(1.0, 1.0 / u_aspect);
    float d = length(p);
    
    // Concentric waves from center + directional tilt waves
    float wave1 = sin(d * 42.0 - u_time * 4.5);
    float wave2 = cos(dot(uv, vec2(16.0, 22.0)) - u_time * 3.2 + dot(tilt, vec2(8.0, -8.0)));
    float wave = (wave1 * 0.6 + wave2 * 0.4) * 0.012 * params.x;
    
    // Normal of the water surface
    vec2 waterNorm = vec2(wave1, wave2) * 0.025 * params.x;
    
    // Refraction through water
    vec3 waterPhoto = photo(uv + waterNorm * params.y, 0.0);
    
    // Sun specular glint on waves
    vec3 lightDir = normalize(vec3(tilt.x * 2.0, -tilt.y * 2.0 + 0.5, 0.9));
    float spec = pow(max(0.0, dot(vec3(waterNorm * 25.0, 1.0), lightDir)), 32.0) * 0.85 * params.x;
    
    waterPhoto = mix(waterPhoto, waterPhoto * vec3(0.88, 0.98, 1.05) + vec3(0.01, 0.03, 0.05), 0.25);
    return waterPhoto + vec3(spec);
}
