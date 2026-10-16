// Zen Pond: the photo lies under the still surface of a pond. Drops fall at random places and send out rings
// that spread, weaken and fade; a faint breeze ripples the whole surface. The surface bends the view of the
// photo, catches the sky's sheen where it slopes and flashes sun glints, which move with the tilt.
// Sliders: waves (how many drops and how strong), refraction.

// Rings: one drop per cell, each on its own clock. Returns the surface gradient (x, y) and height (z).
vec3 pdRings(vec2 q,float t,float amount){
    const float CELL=.34,PERIOD=4.2,SPEED=.085,WAVELENGTH=.04;
    vec2 cell=floor(q/CELL);vec3 s=vec3(0.0);
    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
        vec2 id=cell+vec2(float(x),float(y)),h=hash2(id*1.31+.7);
        // Fewer drops at low settings: a cell skips its drop when its die roll is above the amount.
        if(h.y>.15+.6*amount)continue;
        float clock=t/PERIOD+h.x*7.0,age=fract(clock)*PERIOD;
        vec2 jitter=hash2(id+floor(clock)*.17)-.5;                 // a new place for each drop
        vec2 centre=(id+.5+jitter*.8)*CELL;
        vec2 d=q-centre;float r=length(d)+1e-5;
        float front=age*SPEED;
        // A short train of crests behind the front, damped with age and with distance travelled.
        float x0=(r-front)/WAVELENGTH;
        float env=exp(-x0*x0*.12)*smoothstep(1.5,.5,x0)*exp(-age*.6)*smoothstep(0.0,.15,age);
        float wave=sin(x0*6.2832)*env;
        float slope=cos(x0*6.2832)*6.2832/WAVELENGTH*env;
        s.z+=wave;s.xy+=slope*d/r;
    }
    return s;
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float waves=params.x,refraction=params.y;
    float t=u_time;
    vec2 q=(uv-.5)*vec2(u_aspect,1.0);

    // Surface slope: drop rings plus a faint breeze of crossing ripples.
    vec3 r=pdRings(q,t,waves);
    vec2 breeze=vec2(cos(dot(q,vec2(14.0,5.0))-t*.9),cos(dot(q,vec2(-6.0,13.0))-t*.75))*vec2(14.0,13.0)*.0035;
    vec2 grad=r.xy*.0021*(.5+.5*waves)+breeze*(.3+.7*waves);

    // Refraction through the surface.
    vec2 offset=grad*mix(.004,.02,refraction);
    vec3 under=photo(uv+offset/vec2(u_aspect,1.0),0.0);
    // Pond water: a little darker and greener than air, with depth toward the bottom of the screen.
    under=mix(under,under*vec3(.86,.95,.97)+vec3(.0,.015,.02),.35+.2*(1.0-uv.y));

    // Surface normal from the slope (normalized: the glint must never exceed one).
    vec3 n=normalize(vec3(-grad,1.0));
    vec3 eye=vec3(0.0,0.0,1.0);
    // Sky sheen: slopes facing the sky reflect more of its soft light (Schlick Fresnel on the tilted normal).
    float cosT=clamp(dot(n,normalize(eye+vec3(tilt*.25,0.0))),0.0,1.0);
    float fres=.02+.98*pow(1.0-cosT,5.0);
    vec3 sky=mix(vec3(.78,.86,.95),photo(vec2(.5,.92),8.0),.35);
    vec3 col=mix(under,sky,clamp(fres*5.0,0.0,.22));
    // Sun glints: the sun sits up and behind the viewer, swinging with the tilt.
    vec3 sun=normalize(vec3(tilt.x*.9+.2,tilt.y*.9+.55,1.0));
    vec3 h=normalize(sun+eye);
    float spec=pow(max(dot(n,h),0.0),70.0);
    col+=vec3(1.0,.98,.93)*spec*.35*(.35+.65*waves);
    // Ring crests catch a soft highlight of their own, so even a calm photo shows the drops.
    col+=vec3(.9,.95,1.0)*max(r.z,0.0)*.035*(.4+.6*waves);
    return col;
}
