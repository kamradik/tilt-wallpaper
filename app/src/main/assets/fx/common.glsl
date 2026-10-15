#version 300 es
precision highp float;
// Shared header for every effect in fx/. GlScene appends one effect file and the main() below it,
// so each effect compiles as its own small program instead of a branch of one uber-shader.
uniform sampler2D u_image;
uniform sampler2D u_depth;
uniform sampler2D u_wipeMask; // condensation: 64x128 screen-space wipe coverage, decaying back to fog
uniform vec2 u_imageSize;
uniform vec2 u_coverScale;
uniform vec2 u_crop;
uniform float u_zoom;
uniform float u_aspect;
// Fixed-observer ray projection, adapted from DuoLikeAnimation (MIT).
uniform vec3 u_projectionX;
uniform vec3 u_projectionY;
uniform vec3 u_projectionZ;
uniform vec3 u_gap;
uniform vec3 u_blurGap;
uniform float u_screenWidthMm;
uniform vec2 u_tilt;      // smoothed tilt, -1..1
uniform vec4 u_touch;     // normalized xy, age seconds, enabled
uniform vec2 u_params;    // the effect's two sliders, 0..1
uniform float u_toggle;   // the effect's switch, 0 or 1
uniform float u_motion;   // 1, or 0.15 with Reduce Motion
uniform float u_time;     // seconds since the renderer started
uniform vec2 u_wake;      // seconds since the wallpaper became visible, active
uniform float u_alpha;
uniform sampler2D u_field;  // Simulation field: 72x160 RGBA, screen space (row 0 at the bottom); see Simulation.java
uniform vec3 u_gravity;     // screen-space gravity: xy (x right, y up, length 0 flat .. 1 upright), z shake 0..1
uniform vec2 u_charge;      // x 1 while charging, y battery level 0..1
uniform float u_day;        // local time of day 0..1 (0 midnight, .5 noon)    // crossfade weight while switching effects
in vec2 v_uv;
out vec4 outColor;

// Cover-fit with crop and zoom; clamping to the texel centre keeps tilted samples on the photo,
// so motion stretches the edge pixels instead of revealing black borders.
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

vec2 hash2(vec2 p){
    p=vec2(dot(p,vec2(127.1,311.7)),dot(p,vec2(269.5,183.3)));
    return fract(sin(p)*43758.5453123);
}
