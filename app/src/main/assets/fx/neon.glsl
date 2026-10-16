// Neon Outline: the subject's outline lights up as a neon tube. A bright pulse runs round it, the colour drifts
// slowly along the tube, and its glow spills onto the darkened, softened background behind; the tube flickers on
// when the screen wakes. Without a subject mask the outline follows the strongest depth edges instead.
// Sliders: brightness, colour (where on the neon palette the tube sits).

vec3 neHue(float h){return clamp(abs(fract(h+vec3(0.0,.667,.333))*6.0-3.0)-1.0,0.0,1.0);}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float bright=params.x,hue=params.y;float t=u_time*u_motion;
    vec2 fu=uv-tilt*.004;
    float m=subjectAt(fu);
    // The outline: where the (slightly blurred) mask passes one half, measured by its gradient.
    vec2 e=vec2(.0025,0.0);
    float mx=subjectAt(fu+e.xy)-subjectAt(fu-e.xy),my=subjectAt(fu+e.yx)-subjectAt(fu-e.yx);
    float edge=clamp(length(vec2(mx,my))*6.0,0.0,1.0);
    // A wide halo from a blurred copy of the mask's edge.
    float soft=subjectSoft(fu,3.0);
    float halo=clamp(1.0-abs(soft-.5)*2.2,0.0,1.0);
    // Position along the outline: the angle around the frame's middle, for the pulse and the colour drift.
    vec2 c=(uv-vec2(.5,.5))*vec2(u_aspect,1.0);float ang=atan(c.y,c.x)/6.28318+.5;
    vec3 tube=mix(neHue(hue+ang*.25+t*.02),vec3(1.0),.25);
    float pulse=pow(.5+.5*cos((ang-t*.12)*6.28318),8.0);
    // Flicker on wake: a few dropouts in the first second.
    float on=u_wake.x<1.2?step(.35,fract(sin(floor(u_wake.x*14.0)*91.7)*43758.5)):1.0;
    // Background: darkened and soft; the subject a little darker too, so the tube reads.
    vec3 base=photo(fu,0.0),back=photo(fu,2.5)*.32;
    vec3 col=mix(back,base*.82,m);
    col+=tube*halo*.35*bright*on;
    col+=tube*edge*(1.2+1.6*pulse)*bright*on;
    col+=vec3(1.0)*edge*pulse*.5*bright*on;
    return col;
}
