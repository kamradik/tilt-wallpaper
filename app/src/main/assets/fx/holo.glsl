// Holographic Foil: the photo printed on a holographic trading card. The picture stays readable; a broad band
// of rainbow sheen sweeps across it as the phone tilts (the angle between light, foil and eye changes), its
// colours running in the order a diffraction grating gives, and catching more on light areas and edges.
// Sliders: strength, threshold (how bright an area must be to shine). Switch: micro-ridges, the fine pattern
// of the foil, which shimmers inside the band.

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float power=params.x;
    const vec3 Y=vec3(.2126,.7152,.0722);
    vec3 base=photo(uv,0.0);
    float lum=dot(base,Y);
    vec2 q=(uv-.5)*vec2(u_aspect,1.0);

    // The sheen band: a wide diagonal that the tilt sweeps across the card.
    vec2 dir=normalize(vec2(.6,1.0));
    float across=dot(q,dir)-(tilt.x*.55-tilt.y*.35);
    float band=exp(-across*across*9.0);
    // Diffraction colour: hue runs along the band and shifts with the tilt; pastel, as real foil looks.
    float hue=across*2.2+dot(q,vec2(-dir.y,dir.x))*.6+tilt.x*.9;
    vec3 rainbow=.55+.45*cos(6.2832*(hue+vec3(0.0,.33,.67)));
    rainbow=mix(vec3(dot(rainbow,Y)),rainbow,1.15);

    // Where it shines: lighter areas above the threshold, and edges, which diffract strongly on foil.
    float threshold=mix(.15,.7,params.y);
    float shine=smoothstep(threshold-.15,threshold+.25,lum);
    float edge=clamp(length(base-photo(uv+vec2(1.5)/u_imageSize,0.0))*3.0,0.0,1.0);
    float k=(band*1.1+.1)*mix(.45,1.0,max(shine,edge*.8))*power;

    if(u_toggle>.5){
        // Micro-ridges: fine diagonal lines that brighten and darken the sheen as they catch the light.
        float ridges=sin(dot(q,vec2(-dir.y,dir.x))*420.0+tilt.x*9.0+sin(q.y*30.0+u_time*.8)*1.5);
        k*=.8+.35*ridges*band;
    }
    vec3 sheen=clamp(rainbow,0.0,1.0)*k*.95;
    // Screen the sheen on, with a hint of the foil's own metallic lift on the brightest spots.
    vec3 col=1.0-(1.0-base)*(1.0-clamp(sheen,0.0,1.0));
    return col+vec3(1.0)*pow(band,6.0)*shine*.08*power;
}
