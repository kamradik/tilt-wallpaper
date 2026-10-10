vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Submerged / Caustic Light: Crystal pool caustics with sunlight refraction threads and water ripples
    vec2 wuv = uv * 7.5 + vec2(tilt.x * 0.8, -tilt.y * 0.8);
    
    // Two intersecting dynamic wave fields creating Voronoi caustic threads
    vec2 p1 = wuv + vec2(u_time * 0.45, u_time * 0.28);
    vec2 p2 = wuv * 1.25 - vec2(u_time * 0.32, -u_time * 0.42);
    
    vec2 id1 = floor(p1); vec2 f1 = fract(p1) - 0.5;
    vec2 id2 = floor(p2); vec2 f2 = fract(p2) - 0.5;
    
    float d1 = 9.0;
    for(int y = -1; y <= 1; y++){
        for(int x = -1; x <= 1; x++){
            vec2 n = vec2(float(x), float(y));
            vec2 r = hash2(id1 + n);
            d1 = min(d1, length(f1 - (n + (r - 0.5) * 0.75)));
        }
    }
    float d2 = 9.0;
    for(int y = -1; y <= 1; y++){
        for(int x = -1; x <= 1; x++){
            vec2 n = vec2(float(x), float(y));
            vec2 r = hash2(id2 + n);
            d2 = min(d2, length(f2 - (n + (r - 0.5) * 0.75)));
        }
    }
    
    // Caustic light rays focus sharply along cell boundaries
    float caustic = pow(min(d1, d2), 1.9);
    caustic = smoothstep(0.02, 0.45, caustic);
    float causticIntensity = pow(1.0 - caustic, 3.2) * 2.8 * params.x;
    
    // Surface ripples from device tilt and motion
    float ripple = sin(dot(uv - 0.5, vec2(18.0, 22.0)) - u_time * 3.5 + length(tilt) * 6.0) * 0.008;
    ripple += sin(dot(uv - 0.5, vec2(-24.0, 16.0)) + u_time * 2.8) * 0.005;
    float rippleToggle = (u_toggle > 0.5) ? 1.0 : 0.0;
    vec2 rippleOffset = vec2(ripple, -ripple) * ((0.4 + params.y * 1.2) * rippleToggle);
    
    // Refracted art under crystal water
    vec3 submerged = photo(uv + rippleOffset, 0.0);
    
    // Shallow water spectral absorption & radiant turquoise pool glow
    submerged = mix(submerged, submerged * vec3(0.82, 0.96, 1.10) + vec3(0.01, 0.04, 0.08), 0.35 * params.y);
    vec3 causticColor = vec3(0.85, 1.15, 1.35) * causticIntensity;
    
    // Specular glint on water ripples
    float waterSpec = pow(max(0.0, dot(normalize(vec3(tilt.x * 2.0, -tilt.y * 2.0, 1.0)), vec3(ripple * 30.0, -ripple * 30.0, 1.0))), 28.0) * 0.7 * params.x;
    
    return submerged + causticColor + vec3(waterSpec);
}
