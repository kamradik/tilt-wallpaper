vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    // Holographic foil: diffraction rainbow on highlights and edges (tilt-driven), plus optional
    // embossed micro-ridges that shimmer over time.
    vec3 base=photo(uv,0.0);
    float luma=dot(base,vec3(0.2126,0.7152,0.0722));
    vec2 p=(uv-.5)*vec2(1.0,1.0/u_aspect);
    float phase=dot(p,vec2(14.0,20.0))+dot(tilt,vec2(4.5,3.2))+luma*3.8;
    float grating=sin(dot(p,vec2(120.0,-90.0))+phase*2.0);
    vec3 rainbow=0.5+0.5*cos(6.28318*(phase*0.28+vec3(0.0,0.33,0.67)));
    float threshold=mix(0.22,0.68,params.y);
    float highlight=smoothstep(threshold,threshold+0.32,luma);
    float edge=length(base-photo(uv+vec2(1.5)/u_imageSize,0.0))*2.8;
    float mask=clamp(highlight*1.3+edge*0.7,0.0,1.0);
    vec3 sheen=rainbow*(0.75+0.25*grating)*params.x*mask;
    vec3 result=1.0-(1.0-base)*(1.0-sheen*1.4);
    if(u_toggle>0.5){
        float ridges=dot(uv*vec2(1.0,u_aspect),vec2(0.707))*mix(18.0,60.0,params.y)+tilt.x*4.0-tilt.y*3.0+sin(uv.x*12.0+u_time);
        vec3 foil=0.5+0.5*cos(ridges+vec3(0.0,2.094,4.188));
        float gleam=pow(max(0.0,sin(ridges*0.5)),12.0)*1.5;
        result=mix(result,foil*(base+0.25)+vec3(gleam),smoothstep(0.25,0.80,luma)*0.45*params.x);
    }
    return result;
}
