#version 300 es
precision highp float;
uniform sampler2D u_image;
uniform sampler2D u_depth;
uniform vec2 u_imageSize;
uniform vec2 u_coverScale;
uniform vec2 u_crop;
uniform float u_zoom;
uniform float u_aspect;
uniform vec3 u_projectionX;
uniform vec3 u_projectionY;
uniform vec3 u_projectionZ;
uniform vec3 u_gap;
uniform vec3 u_blurGap;
uniform float u_screenWidthMm;
uniform vec2 u_tilt;
uniform vec4 u_touch; // normalized xy, age seconds, enabled strength
uniform vec2 u_params;
uniform vec2 u_previousParams;
uniform int u_effect;
uniform int u_previous;
uniform float u_transition;
uniform float u_motion;
in vec2 v_uv;
out vec4 outColor;

vec2 coords(vec2 uv){
    vec2 span=u_coverScale/u_zoom;
    vec2 origin=mix(span*.5,vec2(1.0)-span*.5,u_crop);
    vec2 p=origin+(uv-.5)*span;
    return clamp(vec2(p.x,1.0-p.y),.5/u_imageSize,1.0-.5/u_imageSize);
}
vec3 photo(vec2 uv,float lod){return textureLod(u_image,coords(uv),max(0.0,lod)).rgb;}
float depthAt(vec2 uv){return texture(u_depth,coords(uv)).r;}
vec3 classic(vec2 uv){
    vec3 point=vec3((uv-.5)*vec2(1.0,1.0/u_aspect),1.0);
    float z=max(.01,dot(u_projectionZ,point));
    vec2 hit=vec2(dot(u_projectionX,point),dot(u_projectionY,point))/z;
    vec2 imageUV=hit*vec2(1.0,u_aspect)+.5;
    float gap=max(0.0,dot(u_blurGap,point));
    float sigma=.12*gap/u_screenWidthMm*u_coverScale.x/u_zoom*u_imageSize.x;
    float lod=max(1.0,log2(max(sigma,1.0)));
    vec3 color=mix(photo(imageUV,0.0),photo(imageUV,lod),smoothstep(0.0,2.0,sigma));
    return color*max(.35,1.0-.0108*max(0.0,dot(u_gap,point)));
}
vec3 effect(int mode,vec2 params){
    vec2 uv=v_uv;vec2 tilt=u_tilt*u_motion;
    if(mode==0)return classic(uv);
    if(mode==1){
        vec2 move=tilt*(.015+.05*params.y)*params.x;
        // Inverse warp with two bounded refinement steps; no forward-warp holes.
        vec2 q=uv-move*(depthAt(uv)-.35);
        q=uv-move*(depthAt(q)-.35);
        return photo(q,0.0);
    }
    if(mode==2){
        vec3 base=photo(uv,0.0);
        vec2 center=vec2(.48,.52)+tilt*.28;
        vec2 p=(uv-center)*vec2(1.0,1.0/u_aspect);
        float angle=-.5+tilt.x*.28;vec2 axis=vec2(cos(angle),sin(angle));
        float narrow=dot(p,axis),longitudinal=dot(p,vec2(-axis.y,axis.x));
        float width=mix(.16,.6,params.y);
        float glow=exp(-narrow*narrow/(width*width)-longitudinal*longitudinal/2.4)*params.x*.24;
        vec3 sheen=vec3(.98,.94,.86)*glow;
        float fringe=.002*exp(-abs(narrow)/width)*params.x;
        sheen+=vec3(fringe,-fringe*.2,-fringe);
        return 1.0-(1.0-base)*(1.0-sheen);
    }
    if(mode==3){
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
    float focus=clamp(.5+tilt.x*params.y*.6,.05,.95);
    float coc=abs(depthAt(uv)-focus);
    float sigma=coc*coc*params.x*44.0;
    float lod=log2(max(1.0,sigma));
    return mix(photo(uv,0.0),photo(uv,lod),smoothstep(.015,.12,coc));
}
void main(){
    vec3 color=effect(u_effect,u_params);
    if(u_transition<.999)color=mix(effect(u_previous,u_previousParams),color,smoothstep(0.0,1.0,u_transition));
    outColor=vec4(clamp(color,0.0,1.0),1.0);
}
