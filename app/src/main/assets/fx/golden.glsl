// Golden Hour: the photo in low evening sun. A warm grade that keeps skin and whites natural (it warms shadows
// and midtones more than highlights), a soft sun glow low in the frame that follows the tilt, a gentle bloom
// on what is already bright, and a thin anamorphic streak through the sun. Light is screened on, so nothing
// burns to white. Sliders: strength, flare length.

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float power=params.x;
    const vec3 Y=vec3(.2126,.7152,.0722);
    vec3 base=photo(uv-tilt*.006,0.0);
    float lum=dot(base,Y);

    // Warm grade: amber in the shadows and mids, highlights nearly untouched.
    vec3 warm=base*mix(vec3(1.10,1.0,.80),vec3(1.03,1.0,.95),smoothstep(.45,.95,lum));
    vec3 col=mix(base,warm,.55+.35*power);

    // The sun: low in the frame, swinging with the tilt.
    vec2 sun=vec2(.5+tilt.x*.55,.3+tilt.y*.25);
    vec2 d=(uv-sun)*vec2(u_aspect,1.0);
    float r=length(d);
    float glow=exp(-r*3.2)*.55+exp(-r*r*40.0)*.35;
    float streak=exp(-abs(d.y)*mix(90.0,260.0,1.0-params.y))*exp(-abs(d.x)*mix(3.5,1.2,params.y))*.5;
    vec3 bloom=photo(uv,3.0);
    float bloomK=smoothstep(.55,.95,dot(bloom,Y))*.35;
    vec3 light=vec3(1.0,.66,.30)*glow+vec3(1.0,.8,.5)*streak+bloom*vec3(1.0,.75,.45)*bloomK;
    // Less added light on bright areas, then screened on.
    light*=power*(1.0-.55*smoothstep(.5,.95,lum));
    return 1.0-(1.0-col)*(1.0-clamp(light,0.0,1.0));
}
