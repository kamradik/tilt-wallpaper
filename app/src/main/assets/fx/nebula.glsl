vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Celestial Star Nebula: Interstellar dust clouds and sparkling starfield with 3D parallax & Subject 3D Cutout
    float d = depthAt(uv);
    float fgMask = smoothstep(0.46, 0.54, d);
    
    vec2 fgMove = -tilt * (0.028 * 0.15 * params.x);
    vec2 bgMove = tilt * (0.028 * 1.0 * params.x);
    vec2 nebUv = mix(uv + bgMove, uv + fgMove, fgMask * 0.2);
    
    vec3 fgColor = photo(uv + fgMove, 0.0);
    vec3 bgColor = photo(uv + bgMove, 0.0);
    
    // Soft contact shadow on space background behind subject
    float shadowOcclusion = smoothstep(0.48, 0.52, depthAt(uv - tilt * 0.025)) * (1.0 - fgMask);
    bgColor = mix(bgColor, bgColor * 0.55, shadowOcclusion * 0.65);
    vec3 base = mix(bgColor, fgColor, fgMask);
    
    // Multi-octave nebula volumetric clouds
    vec2 nUv = nebUv * 4.0 + tilt * 0.4;
    float cloud1 = sin(nUv.x * 1.5 + sin(nUv.y * 1.8 + u_time * 0.4));
    float cloud2 = cos(nUv.y * 1.8 - cos(nUv.x * 1.5 + u_time * 0.3));
    float nebulaDensity = smoothstep(-0.2, 0.8, cloud1 + cloud2) * 0.75 * params.x * (1.0 - fgMask * 0.65);
    
    // Cosmic palette: deep ultraviolet, cobalt blue, and radiant magenta
    vec3 nebulaColor = mix(vec3(0.10, 0.45, 1.1), vec3(1.0, 0.22, 0.75), 0.5 + 0.5 * cloud1);
    
    // Micro starfield with sparkling points (mostly in deep background space)
    vec2 starGrid = floor(nebUv * vec2(120.0, 120.0 * u_aspect));
    vec2 starUv = fract(nebUv * vec2(120.0, 120.0 * u_aspect)) - 0.5;
    float starRand = hash2(starGrid).x;
    float star = step(0.96, starRand) * smoothstep(0.18, 0.02, length(starUv)) * (0.6 + 0.4 * sin(u_time * 4.0 + starRand * 100.0)) * (1.0 - fgMask * 0.85);
    
    vec3 result = base + nebulaColor * nebulaDensity + vec3(star * 1.2 * params.y);
    return result;
}
