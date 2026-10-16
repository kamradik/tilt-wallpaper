// Aurora: curtains of the northern lights hang in the upper part of the picture, where the sky usually is.
// Each curtain has a bright green lower edge with fine vertical rays rising from it and fading into violet;
// they ripple slowly and drift with the tilt. Light is screened on and gives way over bright areas, so a face
// or a sunlit wall is not painted over. Sliders: brightness, saturation.

float auCurtain(vec2 p,float base,float speed,float seed,float t){
    // The lower edge wanders; rays rise from it.
    float edge=base+.05*sin(p.x*3.1+t*speed+seed)+.03*sin(p.x*7.3-t*speed*1.3+seed*2.0)+.015*sin(p.x*17.0+t*.7);
    float above=p.y-edge;
    float body=smoothstep(-.012,.006,above)*exp(-max(above,0.0)*mix(5.5,9.0,fract(seed)));
    float rays=.55+.45*pow(.5+.5*sin(p.x*95.0+sin(p.x*13.0+t*.6+seed)*3.0),3.0);
    float fold=.6+.4*sin(p.x*5.0+t*speed*.8+seed*3.0);
    return body*rays*fold;
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float power=params.x,sat=params.y;
    float t=u_time*.35;
    vec3 base=photo(uv-tilt*.006,0.0);
    vec2 p=vec2((uv.x-.5)*u_aspect+tilt.x*.12,uv.y-tilt.y*.06);

    float a=auCurtain(p,.62,1.0,.37,t),b=auCurtain(p*vec2(1.3,1.0)+vec2(.4,0.0),.7,.8,1.91,t*1.2)*.7;
    float glow=a+b;
    // Colour by height above each curtain's edge: green low, violet high.
    float h=clamp((uv.y-.6)*3.0,0.0,1.0);
    vec3 green=vec3(.18,1.0,.55),violet=vec3(.75,.3,1.0);
    vec3 col=mix(green,violet,h*h);
    col=mix(vec3(dot(col,vec3(.333))),col,.55+.45*sat);
    // Fades out toward the lower part of the screen, and over bright areas of the photo.
    float lum=dot(base,vec3(.2126,.7152,.0722));
    float k=glow*power*1.7*smoothstep(.35,.6,uv.y)*(1.0-.5*smoothstep(.55,.95,lum));
    // A faint sky glow under the curtains.
    k+=.06*power*smoothstep(.45,.75,uv.y)*(1.0-lum);
    return 1.0-(1.0-base)*(1.0-clamp(col*k,0.0,1.0));
}
