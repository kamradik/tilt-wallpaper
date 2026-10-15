#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
// Copies the decoder's external frame into a layer's 2D texture (row 0 = top of the picture, like an uploaded
// Bitmap), so effects sample video through the same photo()/mip path as stills. The SurfaceTexture transform
// applies crop and rotation metadata; its coordinates have the picture's bottom at t=0, hence the flip.
// Near the end of a loop the frame fades into u_loop, a copy of the clip's first frame taken from this same
// target (so it is sampled at v_uv, already upright), which hides MediaPlayer's stall at the wrap.
uniform samplerExternalOES u_video;
uniform mat4 u_transform;
uniform sampler2D u_loop;
uniform float u_mix;
in vec2 v_uv;
out vec4 outColor;
void main() {
    vec2 st = (u_transform * vec4(v_uv.x, 1.0 - v_uv.y, 0.0, 1.0)).xy;
    vec3 color = texture(u_video, st).rgb;
    if (u_mix > 0.0) color = mix(color, texture(u_loop, v_uv).rgb, u_mix);
    outColor = vec4(color, 1.0);
}
