// Ferrofluid: a pool of black magnetic liquid lies on the photo and runs downhill with the tilt. A finger is a
// magnet: the pool flows toward it and stands up in sharp spikes that point at it, then relaxes into a round,
// glossy drop when let go. The liquid is a mirror: it reflects the photo along its surface normal, brightest at
// grazing angles, with a hard specular highlight.
// Sliders: volume (how much liquid), spikes (how strongly the magnet pulls it into points).

float ffField(vec2 p,vec2 c,float r,vec2 magnet,float pull,float spikes){
    vec2 d=p-c;float a=atan(d.y,d.x);
    // Spikes: the outline swells at 14 angles, most on the side facing the magnet.
    vec2 toward=magnet-c;float facing=pull>0.0?.5+.5*cos(a-atan(toward.y,toward.x)):0.0;
    // Spikes grow from the outline only: the middle of the pool stays a smooth dome (no seam at its centre).
    float crown=pow(abs(sin(a*7.0+pull*.4)),10.0)*spikes*pull*(.35+.65*facing)*smoothstep(r*.55,r*1.05,length(d));
    float rr=r*(1.0+crown*.9+pull*facing*.25);
    return rr*rr/(dot(d,d)+r*r*.12);  // softened at the centre, so the dome has a clean top
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float volume=params.x,spikes=params.y;float t=u_time*u_motion;
    vec2 p=vec2(uv.x*u_aspect,uv.y);
    // The pool settles downhill: gravity in screen space, with a slow breathing wobble.
    vec2 g=u_gravity.xy;vec2 rest=vec2(.5*u_aspect,.5)+vec2(g.x,g.y)*.22;
    // The magnet: the finger while it is down or just lifted.
    vec2 magnet=u_touch.xy*vec2(u_aspect,1.0);magnet.y=1.0-magnet.y;
    float pull=u_touch.w>.5?exp(-max(0.0,u_touch.z)*1.4):0.0;
    vec2 c=mix(rest,magnet,pull*.55)+vec2(sin(t*.7),cos(t*.53))*.008;
    float r=(.11+.11*volume);
    float f=ffField(p,c,r,magnet,pull,spikes)+.35*ffField(p,c+vec2(.12,-.04)*r/.16,r*.55,magnet,pull*.5,spikes);
    vec3 photoCol=photo(uv,0.0);
    // Height and normal of the liquid surface from the field.
    float h=smoothstep(1.0,2.6,f);float inside=smoothstep(.97,1.03,f);
    vec2 e=vec2(.002,0.0);
    float fx=ffField(p+e.xy,c,r,magnet,pull,spikes)-ffField(p-e.xy,c,r,magnet,pull,spikes);
    float fy=ffField(p+e.yx,c,r,magnet,pull,spikes)-ffField(p-e.yx,c,r,magnet,pull,spikes);
    vec3 n=normalize(vec3(-fx,-fy,.09+.6*h));
    // The mirror: the photo reflected along the normal, blurred a little by the liquid's ripples.
    vec2 refl=uv+n.xy*.25/vec2(u_aspect,1.0);
    vec3 env=photo(refl,1.5);
    float fres=.12+.88*pow(1.0-n.z,3.0);
    vec3 liquid=vec3(.012,.012,.016)+env*fres*.85;
    vec3 L=normalize(vec3(-.4-tilt.x*.6,.7-tilt.y*.6,.6));
    vec3 H=normalize(L+vec3(0,0,1));
    liquid+=vec3(1.0)*pow(max(dot(n,H),0.0),80.0)*1.2+vec3(.9,.95,1.0)*pow(max(dot(n,H),0.0),12.0)*.12;
    // A soft contact shadow around the pool.
    float halo=smoothstep(.45,1.0,f)*(1.0-inside);
    vec3 col=photoCol*(1.0-.45*halo);
    return mix(col,liquid,inside);
}
