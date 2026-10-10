void main(){
    outColor=vec4(clamp(effect(v_uv,u_tilt*u_motion,u_params),0.0,1.0),u_alpha);
}
