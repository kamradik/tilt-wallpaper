// Oil Slick: a thin film of oil drifts over the photo. Where the film is, light reflected from its top and
// bottom interferes, giving the band order of real thin films (straw, magenta, blue, green, ...) as its
// thickness changes; the patches swirl slowly and shift with the tilt (a different viewing angle sees a
// different effective thickness). The colours are laid on with soft light, so the picture stays visible
// through them. Sliders: strength, sheen.

// Thin-film interference colour for an optical thickness in units of ~500 nm.
vec3 olFilm(float t){
    vec3 wavelength=vec3(.65,.53,.45);              // red, green, blue in micrometres
    vec3 phase=6.2832*t*.5/wavelength*2.0;
    return .5+.5*cos(phase+vec3(0.0,.4,.9));
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float power=params.x,gloss=params.y;
    float t=u_time*.12;
    vec3 base=photo(uv,0.0);
    vec2 q=(uv-.5)*vec2(u_aspect,1.0)*2.4;

    // Swirling thickness field (two rounds of domain warping).
    vec2 w=q+.6*vec2(sin(q.y*1.3+t*3.0),cos(q.x*1.1-t*2.6));
    w+=.35*vec2(sin(w.y*2.7-t*2.0+1.7),cos(w.x*2.3+t*2.2));
    float thick=1.1+.8*sin(w.x*1.2+w.y*.9)+.35*sin(w.x*3.1-w.y*2.4+t*4.0);
    // Patches: the film covers part of the surface, with soft, wandering edges.
    float cover=smoothstep(.15,.65,.5+.5*sin(w.x*.8-w.y*1.1+t*1.5)+.25*sin(w.y*2.1+t));
    // The viewing angle (tilt) changes the effective thickness.
    thick*=1.0+.12*dot(tilt,vec2(.8,.6));

    vec3 film=olFilm(thick);
    // Soft light: the film colours the photo without hiding it.
    vec3 soft=mix(2.0*base*film+base*base*(1.0-2.0*film),sqrt(base)*(2.0*film-1.0)+2.0*base*(1.0-film),step(.5,film));
    vec3 col=mix(base,soft,cover*.75*power);
    // Sheen: a glossy highlight on the film where the tilt lines up with the light.
    float glint=pow(max(0.0,sin(thick*3.0+tilt.x*2.0)),10.0)*cover;
    col+=film*glint*.18*gloss*power;
    return col;
}
