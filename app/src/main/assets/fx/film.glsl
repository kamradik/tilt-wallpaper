// Retro Film: the look of cinema stock. A soft S-curve with lifted blacks, a warm-red halation glowing around
// real highlights (the light that scatters back through the film base), a thin anamorphic streak drawn from
// the brightest points, and grain in the midtones. Light is added with screen blending and a shoulder, so
// bright photos keep their detail. Sliders: strength, flare tone (cyan <-> gold). Switch: the streak follows the tilt.

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float power=params.x;
    const vec3 Y=vec3(.2126,.7152,.0722);
    vec3 base=photo(uv,0.0);
    vec3 exposed=base*(1.0+exp(-u_wake.x*7.5)*.35);  // a brief flash as the screen wakes, like a shutter

    // Halation: only light that stands out from its neighbourhood, glowing outward from it.
    vec3 wide=photo(uv,3.4);
    float hi=smoothstep(.72,.98,dot(wide,Y));
    float peak=smoothstep(.82,1.0,dot(base,Y));
    vec3 halation=vec3(1.0,.32,.12)*hi*(1.0-peak*.7)*.55*power;

    // Anamorphic streak: a horizontal line of light through the brightest points.
    vec2 step=vec2(9.0/u_imageSize.x,0.0);
    float streak=0.0;
    for(int i=-4;i<=4;i++){float w=1.0-abs(float(i))/5.0;streak+=smoothstep(.86,1.0,dot(photo(uv+step*float(i)*1.6,1.4),Y))*w;}
    streak/=4.2;
    float sway=u_toggle>.5?.7+.6*abs(tilt.x):.85;
    vec3 flare=mix(vec3(.25,.65,1.0),vec3(1.0,.72,.36),params.y)*streak*sway*.45*power;

    // Film curve: lifted, slightly warm blacks and a gentle S on the midtones.
    vec3 c=mix(exposed,smoothstep(-.05,1.08,exposed),.45+.25*power);
    c=c*(1.0-.05*power)+vec3(.035,.03,.026)*power;
    c*=vec3(1.02,1.0,.96);
    // Screen the light on, so it can never push a pixel past white.
    c=1.0-(1.0-c)*(1.0-clamp(halation+flare,0.0,1.0));

    // Grain, strongest in the midtones.
    float l=dot(c,Y);
    float n=hash2(floor(uv*u_imageSize)+fract(u_time*13.0)*vec2(71.0,37.0)).x-.5;
    c+=n*(.025+.05*power)*4.0*l*(1.0-l);
    return c;
}
