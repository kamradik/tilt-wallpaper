vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Drop Shadow Layering: Floating cutout subject with dynamic gyro drop shadow
    float depth=depthAt(uv);
    float fgMask = subjectAt(uv);
    
    vec2 move=tilt*mix(0.02,0.065,params.x);
    vec2 fgUv=uv-move*0.15;
    vec2 bgUv=uv+move*1.0;
    
    float shadowOcclusion=subjectAt(uv-tilt*0.025)*(1.0-fgMask);
    
    // Silhouette edge bevel highlight
    vec2 texel=1.5/u_imageSize;
    float edgeX=depthAt(uv+vec2(texel.x,0.0))-depthAt(uv-vec2(texel.x,0.0));
    float edgeY=depthAt(uv+vec2(0.0,texel.y))-depthAt(uv-vec2(0.0,texel.y));
    vec2 edgeNorm=vec2(edgeX,edgeY);
    float edgeLen=length(edgeNorm);
    float bevelLight=max(0.0,dot(edgeLen>0.001?edgeNorm/edgeLen:vec2(0.0),normalize(vec2(tilt.x*1.5,-tilt.y*1.5+0.5))));
    vec3 bevel=vec3(1.0)*smoothstep(0.08,0.30,edgeLen)*bevelLight*0.45*params.x;
    
    vec3 bg=photo(bgUv,0.0);
    bg=mix(bg,bg*0.55,shadowOcclusion*mix(0.50,0.85,params.y));
    vec3 fg=photo(fgUv,0.0)+bevel;
    return mix(bg,fg,fgMask);
}
