vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float focus=clamp(.5+tilt.x*params.y*.6,.05,.95);
    float coc=abs(depthAt(uv)-focus);
    float sigma=coc*coc*params.x*44.0;
    float lod=log2(max(1.0,sigma));
    return mix(photo(uv,0.0),photo(uv,lod),smoothstep(.015,.12,coc));
}
