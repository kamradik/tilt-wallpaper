vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    vec2 p=uv*vec2(1.0,1.0/u_aspect);
    vec2 field=vec2(sin(p.y*5.0+tilt.x*1.8),cos(p.x*4.0+tilt.y*1.8));
    vec2 offset=(field*.003+tilt*.012)*params.x*params.y*u_motion;
    vec2 delta=(uv-u_touch.xy)*vec2(1.0,1.0/u_aspect);
    float d=length(delta),age=max(0.0,u_touch.z);
    float ring=sin(d*32.0-age*10.0)*exp(-d*6.0-age*3.5)*u_touch.w;
    offset+=delta/max(d,.05)*ring*.009*params.y*u_motion;
    float frost=params.x*.75;
    vec3 base=photo(uv+offset,frost);
    return base+vec3(.025,.03,.035)*params.x*max(0.0,field.x*.4+ring*.2);
}
