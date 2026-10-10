vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Frosted Winter Ice: Dendritic frost crystals with crisp optical refraction
    vec2 centerUv = uv - 0.5;
    float edgeDist = length(centerUv * vec2(1.0, u_aspect));
    
    // Frost pattern noise (dendritic crystals)
    vec2 frostUv = uv * 18.0;
    float n1 = sin(frostUv.x * 3.0 + sin(frostUv.y * 2.0)) * 0.5 + 0.5;
    float n2 = sin(frostUv.y * 5.0 - cos(frostUv.x * 4.0)) * 0.5 + 0.5;
    float iceCrystals = pow(n1 * n2, 1.6);
    
    float frostGrowth = smoothstep(0.20, 0.75, edgeDist + (iceCrystals - 0.5) * 0.35 * params.x);
    
    // Ice crystal optical refraction
    vec2 iceDisp = vec2(n1 - 0.5, n2 - 0.5) * 0.018 * frostGrowth * params.y;
    vec3 frostedPhoto = photo(uv + iceDisp - tilt * 0.008, frostGrowth * 1.8);
    
    // Sparkling ice needle glints
    float iceGlint = pow(max(0.0, dot(normalize(vec3(tilt * 2.0, 1.0)), vec3(n1 - 0.5, n2 - 0.5, 0.7))), 16.0);
    vec3 iceColor = vec3(0.82, 0.93, 1.05) * (0.3 + 0.7 * frostGrowth);
    
    vec3 result = mix(frostedPhoto, frostedPhoto * iceColor + vec3(iceGlint * 0.45), frostGrowth * 0.85);
    return result;
}
