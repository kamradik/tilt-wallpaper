vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Parallax Occlusion Mapping (POM): True ray-marched height relief with self-occlusion & Subject 3D Cutout
    float depth = depthAt(uv);
    float fgMask = subjectAt(uv);
    
    float depthScale = mix(0.025, 0.095, params.x);
    vec2 viewRay = -tilt * depthScale;
    
    // 18-step linear ray-march through heightmap
    const int STEPS = 18;
    float stepSize = 1.0 / float(STEPS);
    vec2 uvStep = viewRay * stepSize;
    
    vec2 currentUv = uv;
    float currentLayer = 0.0;
    float height = 1.0 - depthAt(currentUv);
    
    for(int i = 0; i < STEPS; i++){
        if(currentLayer >= height) break;
        currentUv += uvStep;
        height = 1.0 - depthAt(currentUv);
        currentLayer += stepSize;
    }
    
    // Secant/binary interpolation for crisp sub-layer surface contact
    vec2 prevUv = currentUv - uvStep;
    float prevLayer = currentLayer - stepSize;
    float prevHeight = 1.0 - depthAt(prevUv);
    float afterDiff = height - currentLayer;
    float beforeDiff = prevHeight - prevLayer;
    float weight = afterDiff / max(0.0001, afterDiff - beforeDiff);
    vec2 finalUv = mix(currentUv, prevUv, weight);
    
    // Self-shadowing: secondary ray-march towards directional light
    float shadowToggle = (u_toggle > 0.5) ? 1.0 : 0.0;
    vec2 lightRay = normalize(vec2(tilt.x * 1.5, -tilt.y * 1.5 + 0.5)) * depthScale * 0.5;
    vec2 shadowStep = lightRay * (1.0 / 8.0);
    vec2 shadowUv = finalUv;
    float shadowHeight = 1.0 - depthAt(finalUv);
    float shadow = 1.0;
    for(int j = 1; j <= 8; j++){
        shadowUv += shadowStep;
        float h = 1.0 - depthAt(shadowUv);
        if(h < shadowHeight - float(j) * (1.0 / 8.0) * 0.4){
            shadow = mix(shadow, 0.45, params.y);
            break;
        }
    }
    
    vec3 bg = photo(finalUv, 0.0) * mix(1.0, shadow, shadowToggle);
    
    // Soft contact shadow on background behind floating subject
    float shadowOcclusion = subjectAt(uv - tilt * 0.025)*(1.0-fgMask);
    bg = mix(bg, bg * 0.55, shadowOcclusion * 0.65);
    
    // Foreground subject stays crisp with delicate 0.15x micro-tilt
    vec2 fgUv = uv - tilt * depthScale * 0.15;
    vec3 fg = photo(fgUv, 0.0);
    
    return mix(bg, fg, fgMask);
}
