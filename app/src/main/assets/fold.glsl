#version 300 es
precision highp float;
uniform sampler2D u_image;
uniform vec2 u_imageSize;
uniform vec2 u_coverScale;
uniform float u_aspect;
// Fixed-observer ray projection, adapted from DuoLikeAnimation (MIT).
uniform vec3 u_projectionX;
uniform vec3 u_projectionY;
uniform vec3 u_projectionZ;
uniform vec3 u_gap;
uniform vec3 u_blurGap;
uniform float u_screenWidthMm;
in vec2 v_uv;
out vec4 outColor;

vec2 imageCoordinates(vec2 uv) {
    vec2 centered=vec2(0.5)+(uv-vec2(0.5))*u_coverScale;
    return vec2(centered.x,1.0-centered.y);
}
vec3 sampleScreen(vec2 uv,float sigma) {
    uv=imageCoordinates(uv);
    float lod=max(0.0,log2(max(sigma,1.0)));
    vec3 blurred=textureLod(u_image,uv,max(1.0,lod)).rgb;
    if(sigma>=2.0)return blurred;
    return mix(textureLod(u_image,uv,0.0).rgb,blurred,smoothstep(0.0,2.0,sigma));
}
void main() {
    if(u_gap.z<=0.00001&&u_blurGap.z<=0.00001){outColor=vec4(texture(u_image,imageCoordinates(v_uv)).rgb,1.0);return;}
    vec2 p=(v_uv-vec2(0.5))*vec2(1.0,1.0/u_aspect);
    vec3 point=vec3(p,1.0);
    float depth=dot(u_projectionZ,point);
    if(depth<=0.00001){outColor=vec4(0.0,0.0,0.0,1.0);return;}
    // Eye-to-glass ray intersected with z=0; both axes project about the eye normal.
    vec2 hit=vec2(dot(u_projectionX,point),dot(u_projectionY,point))/depth;
    vec2 imageUv=hit*vec2(1.0,u_aspect)+vec2(0.5);
    float gapMm=max(0.0,dot(u_blurGap,point));
    float radiusMm=0.12*gapMm;
    // Reuse precomputed Gaussian levels instead of a per-pixel 32-tap disk.
    float radiusUv=radiusMm/u_screenWidthMm;
    float sigma=radiusUv*u_coverScale.x*u_imageSize.x;
    vec2 softness=max(fwidth(imageUv),vec2(radiusUv,radiusUv*u_aspect));
    vec2 bounds=1.0-smoothstep(vec2(0.5)-softness,vec2(0.5)+softness,abs(imageUv-vec2(0.5)));
    float mask=bounds.x*bounds.y;
    float attenuation=max(1.0-0.09*0.12*max(0.0,dot(u_gap,point)),0.0);
    outColor=vec4(sampleScreen(imageUv,sigma)*attenuation*mask,1.0);
}
