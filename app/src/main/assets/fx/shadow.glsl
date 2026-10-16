// Drop Shadow: the subject lifts off the photo and floats above it. It grows a touch, moves with the tilt more
// than the background does, and casts a soft shadow onto the background that slides the other way and spreads as
// it lifts, like a paper cut-out held above a page. Where it moved away the painted background plate shows.
// Sliders: lift, shadow.

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float lift=params.x,shade=params.y;
    vec2 move=tilt*(.018+.04*lift);
    // The floating subject: scaled up about the frame's middle and moved toward the viewer's side.
    vec2 su=(uv-.5)/(1.0+.06*lift)+.5-move*.6;
    float m=subjectAt(su);
    vec3 subject=photo(su,0.0);
    // Background: almost still; behind the subject's old place the plate (or a soft fill).
    vec2 bu=uv+move*.15;
    vec3 back=photo(bu,0.0);
    float covered=subjectAt(bu);
    if(u_hasPlate>.5)back=mix(back,plate(bu,0.0),smoothstep(.05,.6,covered));
    else back=mix(back,photo(bu,4.0),smoothstep(.2,.8,covered)*.6);
    // Shadow: the subject's mask, offset away from the light and blurred more the higher it floats.
    vec2 light=normalize(vec2(-.45,.8)-tilt*.5);
    vec2 sh=su-move*.6-light*(.02+.045*lift)+move*.8;
    float spread=1.5+3.0*lift;
    float s=subjectSoft(sh,spread);
    back*=1.0-s*(.25+.45*shade);
    // A faint rim of light on the subject's edge facing the light.
    vec2 e=vec2(.004,0.0);
    vec2 gr=vec2(subjectAt(su+e.xy)-subjectAt(su-e.xy),subjectAt(su+e.yx)-subjectAt(su-e.yx));
    subject+=vec3(1.0,.98,.94)*clamp(dot(-gr,light),0.0,1.0)*.18;
    return mix(back,subject,m);
}
