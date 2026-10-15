// Charging: while the phone charges, an energy rim glows along the screen's rounded edges and breathes, with light
// travelling up from the bottom. With "Show level" the rim fills up the sides as far as the battery is charged and
// takes its colour from the level; unplugged, only a hairline level remains (or nothing with the switch off).
// The effect is ANIMATED, so it costs a frame every vsync even unplugged; that path is one photo tap plus arithmetic.

float chRoundRect(vec2 p,vec2 halfSize,float radius){
    vec2 q=abs(p)-halfSize+radius;
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
vec3 chLevelColour(float level){
    vec3 low=vec3(1.0,.30,.24),mid=vec3(1.0,.74,.28),high=vec3(.34,.96,.55);
    return mix(mix(low,mid,smoothstep(.12,.3,level)),high,smoothstep(.35,.7,level));
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float glow=params.x,width=params.y;
    bool showLevel=u_toggle>.5;
    float charging=step(.5,u_charge.x),level=clamp(u_charge.y,0.0,1.0);
    vec3 col=photo(uv,0.0);
    if(charging<.5&&!showLevel)return col;

    // Distance in screen heights from the display's rounded outline (positive inside).
    vec2 p=(uv-.5)*vec2(u_aspect,1.0);
    float inside=-chRoundRect(p,vec2(u_aspect*.5,.5),.06*min(1.0,u_aspect*2.2));
    float aa=fwidth(inside)*1.2;
    // Position along the outline: 0 at the bottom centre, 1 at the top centre, the same on both sides.
    float s=atan(p.x,-p.y*u_aspect*1.6)/3.14159;
    float along=abs(s);
    float fill=showLevel?smoothstep(level+.012,level-.012,along):1.0;
    vec3 tint=showLevel?chLevelColour(level):vec3(.40,1.0,.72);

    if(charging<.5){
        // Unplugged: a quiet hairline of the level, no motion.
        float line=1.0-smoothstep(.0015,.0015+aa,abs(inside-.004));
        return mix(col,tint,line*fill*.55*(.4+.6*glow));
    }
    float w=mix(.008,.05,width);
    float t=u_time;
    float breathe=.78+.22*sin(t*2.4);
    // Pulses of light running up both sides toward the top.
    float run=pow(.5+.5*cos((along*2.6-t*.55)*6.2832),5.0);
    // A brighter spark at the head of the charge.
    float head=showLevel?exp(-pow((along-level)/.02,2.0))*(.6+.4*sin(t*6.0)):0.0;
    float d=max(inside,0.0);
    float core=exp(-pow(d/(w*.3),2.0));
    float halo=exp(-d/(w*.9));
    float energy=(core*.9+halo*.6)*(fill*(.55+.65*run)+(1.0-fill)*.08)*breathe+(core+halo*.5)*head;
    energy*=mix(.5,1.6,glow)*(.65+.35*level+(showLevel?0.0:.35*(1.0-level)));
    // The rim is coloured light: it tints the photo toward the level colour (visible on pale photos too),
    // then a white-hot core adds on top.
    col=mix(col,tint*1.1,clamp(energy*.85,0.0,.92));
    return col+vec3(1.0)*core*fill*.35*clamp(energy,0.0,1.5);
}
