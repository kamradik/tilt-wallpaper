// Finishing (the effect constructor's modifiers), applied to whatever the effect drew: film grain, vignette,
// a light leak that drifts with the tilt, and a cool-to-warm tone. All four at zero (also the value of an unset
// uniform) leave the colour untouched, so a profile without modifiers costs one comparison.
uniform vec4 u_mods;   // grain, vignette, glare 0..1; tone -1 (cool) .. 1 (warm)

vec3 finish(vec3 c,vec2 uv){
    if(u_mods.x+u_mods.y+u_mods.z+abs(u_mods.w)<.002)return c;
    vec2 q=(uv-.5)*vec2(u_aspect,1.0);
    // Tone: warm toward amber, cool toward blue, keeping the brightness.
    float tone=clamp(u_mods.w,-1.0,1.0);
    vec3 tint=tone>0.0?vec3(1.07,1.0,.86):vec3(.9,.98,1.1);
    float l0=dot(c,vec3(.2126,.7152,.0722));
    c*=mix(vec3(1.0),tint,abs(tone));
    c*=l0/max(dot(c,vec3(.2126,.7152,.0722)),1e-4);
    // Glare: a warm light leak from the upper corner and a faint streak, both drifting with the tilt.
    vec2 leak=vec2(-u_aspect*.36,.38)+u_tilt*vec2(-.16,.12);
    float g=exp(-dot(q-leak,q-leak)*2.6)+.35*exp(-pow(dot(q-leak,normalize(vec2(.8,-.6))),2.0)*90.0)*exp(-length(q-leak)*1.2);
    c=1.0-(1.0-c)*(1.0-clamp(vec3(1.0,.7,.4)*g*u_mods.z*.95,0.0,1.0));
    // Vignette: a gentle, wide falloff toward the corners.
    float v=length(q*vec2(1.0/max(u_aspect,.01)*.55,1.0)*1.25);
    c*=1.0-u_mods.y*.6*smoothstep(.45,1.15,v);
    // Grain: luminance noise at screen-pixel scale, finer on dark tones as in film.
    vec2 cell=floor(uv*vec2(u_aspect,1.0)*900.0);
    float n=hash2(cell+fract(u_time*7.0)*vec2(113.0,57.0)).x-.5;
    c+=n*u_mods.x*.11*(1.0-.5*smoothstep(.4,1.0,dot(c,vec3(.333))));
    return c;
}

// Ripple reveal (RenderLoop): while u_reveal.w is 1 a new effect arrives as a disc growing from the finger
// (u_reveal.xy, y up) over progress u_reveal.z. Its rim is a ring of water that bends the picture and catches light.
uniform vec4 u_reveal;

void main(){
    vec2 uv=v_uv;float alpha=u_alpha,rim=0.0;
    if(u_reveal.w>.5){
        vec2 q=(v_uv-u_reveal.xy)*vec2(u_aspect,1.0);float d=length(q);
        vec2 far=max(u_reveal.xy,1.0-u_reveal.xy)*vec2(u_aspect,1.0);
        float p=u_reveal.z,radius=length(far)*1.06*p,width=.035+.05*(1.0-p);
        rim=exp(-pow((d-radius)/width,2.0))*(1.0-p*p);
        uv-=(d>1e-4?q/d:vec2(0.0))*vec2(1.0/u_aspect,1.0)*rim*.035;
        alpha=1.0-smoothstep(radius-width*.22,radius+width*.12,d);
    }
    vec3 c=finish(effect(uv,u_tilt*u_motion,u_params),uv);
    c=mix(c,1.0-(1.0-c)*(1.0-vec3(.9,.96,1.0)*.42),rim);
    outColor=vec4(clamp(c,0.0,1.0),alpha);
}
