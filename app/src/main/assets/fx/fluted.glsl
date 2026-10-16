// Fluted Glass: architectural reeded glass in front of the photo. Each vertical rib is a half-cylinder lens: it
// shows a narrow strip of the photo stretched across its width and mirrored at its edges, so the picture breaks
// into repeated soft slices that slide as you tilt. Every rib carries a highlight on the side facing the light and
// a soft shade in the groove, with a little dispersion at the rib edges.
// Sliders: rib density, refraction.

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float ribs=mix(9.0,30.0,params.x),bend=params.y;
    float x=uv.x*ribs,cell=floor(x),f=fract(x)-.5;          // -.5..5 across the rib
    // The rib's surface: a half-cylinder; its slope bends the view sideways, most at the edges.
    float z=sqrt(max(0.0,.25-f*f))*2.0;                   // height 0 at the groove, 1 at the crest
    float slope=f/max(z,.08);
    // What the rib shows: a strip around its centre, offset by the tilt (each rib sees a little differently).
    float view=(cell+.5)/ribs+tilt.x*.02*(1.0+bend);
    float sx=view+(-slope*.6*bend+f*(1.0-bend*.7))/ribs*1.6;
    vec2 s=vec2(sx,uv.y+tilt.y*.006);
    float disp=.0025*bend*clamp(abs(slope),0.0,2.0);
    vec3 col=vec3(photo(vec2(s.x+disp,s.y),.6).r,photo(s,.6).g,photo(vec2(s.x-disp,s.y),.6).b);
    // Light: a highlight on the side of the crest facing the light, a shade in the groove.
    float lightX=-.35-tilt.x*.7;
    float hl=pow(max(0.0,1.0-abs(f-lightX*.35)*6.0),6.0)*z;
    col+=vec3(1.0)*hl*.22;
    col*=.8+.2*z;
    col=mix(col,col*.7,smoothstep(.42,.5,abs(f)));          // the groove line
    return col;
}
