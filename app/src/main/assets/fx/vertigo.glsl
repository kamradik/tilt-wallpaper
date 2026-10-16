vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Dolly Zoom Vertigo: In-camera Hitchcock dolly zoom shifting perspective depth planes on tilt
    float d = depthAt(uv);
    float fgMask = subjectAt(uv);
    float tiltAmp = tilt.y * 0.15 * params.x + tilt.x * 0.04;
    
    // Opposing focal displacement: foreground zooms delicately (0.15x), background recedes/expands (1.0x)
    float parallaxScale = mix(1.0, 0.15, fgMask);
    float depthDisplacement = (d - 0.45) * tiltAmp * parallaxScale;
    vec2 centerUv = uv - 0.5 - tilt * 0.012 * parallaxScale;
    vec2 dollyUv = 0.5 + centerUv * (1.0 + depthDisplacement);
    
    vec3 warped = photo(dollyUv, 0.0);
    
    // Edge optical aberration fringing during deep perspective shift
    float chromaToggle = (u_toggle > 0.5) ? 1.0 : 0.0;
    float edgeDist = length(centerUv * vec2(1.0, u_aspect));
    vec2 chromaDisp = centerUv * (abs(tiltAmp) * edgeDist * 0.10 * params.y * chromaToggle);
    vec3 vertigo;
    vertigo.r = photo(dollyUv - chromaDisp, 0.0).r;
    vertigo.g = warped.g;
    vertigo.b = photo(dollyUv + chromaDisp, 0.0).b;
    
    // Soft contact shadow on background behind subject
    float shadowOcclusion = subjectAt(uv - tilt * 0.025)*(1.0-fgMask);
    vertigo = mix(vertigo, vertigo * 0.55, shadowOcclusion * 0.65);
    
    return vertigo;
}
