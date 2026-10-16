// Frosted Ice: frost grows on the glass from the edges in. Its outline is a branching crystal front (fern-like
// feathers of hoarfrost), the iced parts are milky and scatter the photo behind them, thin new frost near the front
// only softens it, and the crystals sparkle as the tilt moves the light. The middle of the glass stays clear.
// Sliders: frost (how far it has grown), refraction.

float icNoise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    float a=hash2(i).x,b=hash2(i+vec2(1,0)).x,c=hash2(i+vec2(0,1)).x,d=hash2(i+vec2(1,1)).x;return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);}
float icFbm(vec2 p){float v=0.0,a=.5;mat2 r=mat2(.8,.6,-.6,.8);for(int i=0;i<5;i++){v+=a*icNoise(p);p=r*p*2.1;a*=.5;}return v;}
// Feathers: sharp ridges of a rotated noise, as frost needles branching off each other.
float icFeather(vec2 p){float v=0.0,a=.5;mat2 r=mat2(.6,.8,-.8,.6);for(int i=0;i<4;i++){v+=a*(1.0-abs(icNoise(p)*2.0-1.0));p=r*p*2.3;a*=.55;}return v;}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float frost=params.x,bend=params.y;
    vec2 p=vec2(uv.x*u_aspect,uv.y);
    // Distance from the nearest edge of the glass, with a ragged crystal front.
    float edge=min(min(uv.x,1.0-uv.x)*u_aspect,min(uv.y,1.0-uv.y));
    float front=.03+.15*frost;
    float grow=front-edge+(icFbm(p*7.0)-.5)*.12+(icFeather(p*14.0)-.5)*.08;
    float ice=smoothstep(-.02,.05,grow);                  // fully iced behind the front
    float thin=smoothstep(-.09,-.0,grow)*(1.0-ice);        // a thin veil just ahead of it
    // The iced glass scatters the photo: refracted by the crystal relief and blurred.
    float rel=icFeather(p*22.0);
    vec2 g=vec2(icFeather(p*22.0+vec2(.01,0))-rel,icFeather(p*22.0+vec2(0,.01))-rel);
    vec2 off=g*.05*bend;
    vec3 clear=photo(uv,0.0);
    vec3 milky=photo(uv+off,2.5+bend)*.75+vec3(.86,.92,1.0)*.28;
    milky+=vec3(.9,.95,1.0)*smoothstep(.55,.85,rel)*.22;  // the feathers stand out white
    vec3 col=mix(clear,photo(uv+off*.3,1.2)*.9+vec3(.08,.09,.1),thin*.7);
    col=mix(col,milky,ice);
    // Sparkles: crystal facets that catch the light as the tilt moves it.
    vec2 cp=p*90.0;vec2 cell=floor(cp);vec2 h=hash2(cell);
    float facet=step(.95,h.x)*ice*smoothstep(.3,.05,length(fract(cp)-.5));
    float catchLight=pow(max(0.0,cos((h.y*6.283)+tilt.x*7.0+tilt.y*5.0)),24.0);
    col+=vec3(1.0)*facet*catchLight*.9;
    return col;
}
