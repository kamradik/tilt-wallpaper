vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Spectral Prism / Edge Dispersion: Beveled crystal glass splitting high-contrast edges into spectra
    vec2 texel = 1.5 / u_imageSize;
    
    // 3x3 Sobel luminance edge detection
    float s00 = dot(photo(uv + vec2(-texel.x, -texel.y), 0.0), vec3(0.299, 0.587, 0.114));
    float s01 = dot(photo(uv + vec2(0.0, -texel.y), 0.0), vec3(0.299, 0.587, 0.114));
    float s02 = dot(photo(uv + vec2(texel.x, -texel.y), 0.0), vec3(0.299, 0.587, 0.114));
    float s10 = dot(photo(uv + vec2(-texel.x, 0.0), 0.0), vec3(0.299, 0.587, 0.114));
    float s12 = dot(photo(uv + vec2(texel.x, 0.0), 0.0), vec3(0.299, 0.587, 0.114));
    float s20 = dot(photo(uv + vec2(-texel.x, texel.y), 0.0), vec3(0.299, 0.587, 0.114));
    float s21 = dot(photo(uv + vec2(0.0, texel.y), 0.0), vec3(0.299, 0.587, 0.114));
    float s22 = dot(photo(uv + vec2(texel.x, texel.y), 0.0), vec3(0.299, 0.587, 0.114));
    
    float gx = -s00 - 2.0*s01 - s02 + s20 + 2.0*s21 + s22;
    float gy = -s00 - 2.0*s10 - s20 + s02 + 2.0*s12 + s22;
    float edgeStrength = length(vec2(gx, gy));
    float edge = smoothstep(mix(0.08, 0.28, params.y), 0.65, edgeStrength);
    
    // Tilt dispersion vector (Cauchy chromatic refraction)
    float tiltMag = length(tilt);
    vec2 dispDir = tiltMag > 0.001 ? normalize(tilt) : vec2(1.0, 0.0);
    float dispAmount = (tiltMag * 0.025 + 0.003) * params.x * edge;
    
    vec3 prism;
    prism.r = photo(uv - dispDir * dispAmount * 1.35, 0.0).r;
    prism.g = photo(uv, 0.0).g;
    prism.b = photo(uv + dispDir * dispAmount * 1.15, 0.0).b;
    
    // Prismatic violet-cyan secondary dispersion on strong edges
    vec3 violetDisp = photo(uv + dispDir * dispAmount * 1.65, 0.0);
    prism += vec3(0.35, 0.10, 0.45) * violetDisp.b * edge * params.x * 0.6;
    
    // Crystal facet specular highlight along edge normals
    float facetToggle = (u_toggle > 0.5) ? 1.0 : 0.0;
    vec2 edgeNorm = edgeStrength > 0.001 ? normalize(vec2(gx, gy)) : vec2(0.0);
    float facetGlint = pow(max(0.0, dot(edgeNorm, dispDir)), 14.0) * edge * 0.85 * params.x * facetToggle;
    prism += vec3(1.0, 0.98, 0.92) * facetGlint;
    
    return prism;
}
