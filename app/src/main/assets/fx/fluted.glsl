vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Fluted Glass: Architectural reeded fluted glass
    float angle=0.08+tilt.y*0.05;
    vec2 rotUv=vec2(uv.x*cos(angle)-uv.y*sin(angle),uv.x*sin(angle)+uv.y*cos(angle));
    float freq=mix(24.0,75.0,params.x);
    float ribCoord=fract(rotUv.x*freq)-0.5;
    float nx=ribCoord*2.0;
    float nz=sqrt(max(0.01,1.0-nx*nx));
    vec3 normal=normalize(vec3(nx,tilt.y*0.15,nz));
    vec2 refr=vec2(normal.x,normal.y)*(0.012+0.035*params.y)*u_motion;
    vec2 warpedUv=uv+refr+tilt*0.008;
    float lod=params.y*0.75;
    vec3 glassColor;
    glassColor.r=photo(warpedUv+refr*0.08,lod).r;
    glassColor.g=photo(warpedUv,lod).g;
    glassColor.b=photo(warpedUv-refr*0.08,lod).b;
    vec3 lightDir=normalize(vec3(tilt.x*1.5,-tilt.y*1.5,1.0));
    vec3 halfVec=normalize(lightDir+vec3(0.0,0.0,1.0));
    float spec=pow(max(0.0,dot(normal,halfVec)),36.0)*params.y*0.6;
    float groove=1.0-exp(-abs(ribCoord)*8.0)*0.18*params.y;
    return glassColor*groove+vec3(spec);
}
