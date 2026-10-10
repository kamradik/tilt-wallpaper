vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Diamond Fire: High-refraction gemstone internal reflection and spectral sparks
    vec3 base = photo(uv, 0.0);
    
    // Octagonal gemstone facet grid
    vec2 grid = uv * vec2(16.0, 16.0 * u_aspect);
    vec2 fId = floor(grid);
    vec2 fUv = fract(grid) - 0.5;
    
    float dR = length(fUv);
    vec2 facetNormal = dR > 0.001 ? normalize(fUv) : vec2(0.0);
    
    // High-dispersion chromatic spark calculation
    vec2 sparkRay = facetNormal + tilt * 2.8;
    float sparkAngle = dot(normalize(sparkRay), vec2(cos(u_time * 2.0 + fId.x), sin(u_time * 2.0 + fId.y)));
    float spark = pow(max(0.0, sparkAngle), 36.0) * 3.2 * params.x;
    
    // Spectral dispersion colors for the diamond fire
    vec3 fireColor = 0.5 + 0.5 * cos(sparkAngle * 18.0 + vec3(0.0, 2.1, 4.2));
    vec3 diamondSpark = fireColor * spark * (0.5 + 0.5 * hash2(fId).x);
    
    // Facet refraction reflection
    vec3 refracted = photo(uv + facetNormal * 0.012 * params.y, 0.0);
    vec3 result = mix(refracted, base, 0.3) + diamondSpark;
    return result;
}
