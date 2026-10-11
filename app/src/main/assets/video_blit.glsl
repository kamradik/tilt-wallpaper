#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
// Copies the decoder's external frame into a layer's 2D texture (row 0 = top of the picture, like an uploaded
// Bitmap), so effects sample video through the same photo()/mip path as stills. The SurfaceTexture transform
// applies crop and rotation metadata; its coordinates have the picture's bottom at t=0, hence the flip.
uniform samplerExternalOES u_video;
uniform mat4 u_transform;
in vec2 v_uv;
out vec4 outColor;
void main() {
    vec2 st = (u_transform * vec4(v_uv.x, 1.0 - v_uv.y, 0.0, 1.0)).xy;
    outColor = vec4(texture(u_video, st).rgb, 1.0);
}
