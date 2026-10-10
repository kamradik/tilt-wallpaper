vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Thin-Film Oil Slick: Swirling iridescent marbling on wet surface
    vec3 base = photo(uv, 0.0);
    
    // Domain warping for organic marbling fluid swirl
    vec2 q = uv * 3.5 + tilt * 1.5;
    vec2 warp1 = vec2(sin(q.y + u_time * 0.8), cos(q.x - u_time * 0.7));
    vec2 warp2 = vec2(sin(q.x * 1.5 + warp1.y * 2.0), cos(q.y * 1.5 + warp1.x * 2.0));
    float filmThickness = dot(q + warp2 * 1.2, vec2(1.4, 1.8)) * params.x;
    
    // Multi-order thin-film interference colors
    vec3 interference = 0.5 + 0.5 * cos(filmThickness * 3.14159 + vec3(0.0, 1.8, 3.6));
    interference = pow(max(vec3(0.0), interference), vec3(0.90));
    
    float sheen = pow(max(0.0, sin(filmThickness * 2.0)), 8.0) * 0.45 * params.y;
    
    vec3 result = mix(base, interference * (base + 0.35) + vec3(sheen), 0.65 * params.x);
    return result;
}
