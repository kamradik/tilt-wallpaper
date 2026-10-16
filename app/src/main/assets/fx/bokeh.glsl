// Bokeh: the photo thrown out of focus as by a fast lens. The bright points of the picture bloom into discs of
// light, each in the colour of what it came from, brighter at its rim as real lens bokeh is, round or hexagonal by
// the aperture switch. The discs sit at different distances, so they drift against each other with the tilt.
// Sliders: defocus, lights (how many points become discs). Switch: hexagonal aperture.

float bkShape(vec2 d,float r,float hex){
    float circle=length(d);
    vec2 q=abs(d);float hx=max(q.x*.866+q.y*.5,q.y);
    float dist=mix(circle,hx,hex);
    float disc=smoothstep(r,r*.92,dist);
    float rim=smoothstep(r*.72,r*.97,dist)*disc;           // the brighter rim of real bokeh
    return disc*(.65+.55*rim);
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float defocus=params.x,lights=params.y,hex=u_toggle;
    vec3 col=photo(uv-tilt*.004,2.0+defocus*3.5);
    vec2 p=vec2(uv.x*u_aspect,uv.y);
    vec3 glow=vec3(0.0);
    // Three layers of discs at different distances: the near ones are larger and drift more.
    for(int l=0;l<3;l++){
        float k=float(l),cellSize=mix(.085,.16,k/2.0)*(.7+.6*defocus);
        vec2 drift=tilt*mix(.01,.045,k/2.0);
        vec2 q=(p+drift)/cellSize;vec2 cell=floor(q);
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
            vec2 c=cell+vec2(dx,dy);vec2 h=hash2(c+k*17.0);
            vec2 centre=(c+.25+.5*h)*cellSize-drift;vec2 src=vec2(centre.x/u_aspect,centre.y);
            // The disc's light: the photo there, if it is bright enough to bloom.
            // Only points brighter than their surroundings bloom, and only some of those.
            vec3 s=photo(src,4.0);float lum=dot(s,vec3(.299,.587,.114)),around=dot(photo(src,7.0),vec3(.299,.587,.114));
            float on=smoothstep(.04,.2,lum-around+lights*.08)*smoothstep(.35,.6,lum)*step(h.x,.3+.5*lights);
            if(on<.01)continue;
            float r=cellSize*mix(.32,.55,h.x);
            glow+=s*1.15*bkShape(p-centre,r,hex)*on*mix(.35,.55,k/2.0);
        }
    }
    return 1.0-(1.0-col)*(1.0-clamp(glow,0.0,1.0));
}
