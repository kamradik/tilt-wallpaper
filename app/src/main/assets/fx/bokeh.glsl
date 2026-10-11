// Bokeh: the photo is thrown out of focus and its bright spots bloom into aperture-shaped discs.
// Two disc layers sit at different distances, so tilting slides the near layer further than the far one.

// Aperture distance normalised so the disc edge is at 1: a hexagon with vertical flats, or a circle.
float bokehShape(vec2 q,float hex){
    float circle=length(q);
    vec2 a=abs(q);
    float hexagon=max(a.x*.8660254+a.y*.5,a.y)*1.0746;// scaled so its area matches the unit circle's
    return mix(circle,hexagon,hex);
}

// One layer of discs on a jittered grid; only the 2x2 nearest cells can reach a pixel because
// centres stay within the middle of their cell and radii stay under .75 cells.
vec3 bokehLayer(vec2 p,float cells,float seed,float defocus,float lights,float hex,float gain){
    vec2 g=p*cells;
    vec2 base=floor(g-.5);
    float aa=max(fwidth(g.x),fwidth(g.y))*1.25;
    // Each candidate disc gathers the light of the area around its centre: a mip about half a cell wide
    // keeps point lights from vanishing, and a mip two levels coarser tells highlights from pale walls.
    float cellPx=u_imageSize.y*u_coverScale.y/(u_zoom*cells);
    float lod=log2(max(1.0,cellPx*.45));
    float gainLights=mix(.5,1.6,lights);
    vec3 sum=vec3(0.0);
    for(int j=0;j<2;j++)for(int i=0;i<2;i++){
        vec2 cell=base+vec2(float(i),float(j));
        vec2 h=hash2(cell+seed);
        vec2 centre=cell+.3+.4*h;
        vec2 suv=(centre/cells)/vec2(u_aspect,1.0)+.5;
        vec3 c=photo(suv,lod);
        vec3 around=photo(suv,lod+2.0);
        float luma=dot(c,vec3(.2126,.7152,.0722));
        float local=dot(around,vec3(.2126,.7152,.0722));
        // Energy that stands out from its surroundings, plus anything close to clipping.
        float energy=max(0.0,luma-local)*3.0+smoothstep(.93,1.0,luma)*.25;
        // Not every highlight blooms; "Lights" decides how many of them do.
        float keep=step(h.y,mix(.4,.95,lights));
        float w=smoothstep(.03,.35,energy*gainLights)*keep;
        if(w<.004)continue;
        float r=mix(.42,.72,fract(h.x*7.13+h.y*3.7))*mix(.7,1.0,defocus);
        vec2 q=(g-centre)/r;
        float d=bokehShape(q,hex);
        float e=aa/r+.035;
        float disc=1.0-smoothstep(1.0-e,1.0+e,d);
        // Mechanical vignetting: toward the frame edges the disc is clipped into a cat's eye.
        vec2 fromMid=centre/cells;
        float off=length(fromMid)*1.1;
        vec2 dir=length(fromMid)>1e-4?normalize(fromMid):vec2(0.0);
        float d2=length(q+dir*off*.9);
        disc*=1.0-smoothstep(1.35-e,1.35+e,d2);
        // Brighter rim and faint onion rings from aspheric elements, slightly dim core.
        float rim=smoothstep(.55,.98,d);
        float body=.62+.5*rim*rim+.018*sin(d*26.0);
        // Lift colour toward the light itself: saturated but never neon.
        // Colour of the light itself: the excess over its surroundings, normalised, never neon.
        vec3 tint=max(c-around*.8,0.0)+c*.12;
        tint/=max(.05,max(max(tint.r,tint.g),tint.b));
        tint=mix(vec3(dot(tint,vec3(.2126,.7152,.0722))),tint,.85)*.85+.15;
        // Pale scenes get softer discs so a bright sky does not fill with plates; night scenes glow.
        sum+=tint*disc*body*w*gain*(.55+min(energy,1.0))*(1.0-.75*local)*(1.0+1.2*(1.0-smoothstep(0.0,.35,local)));
    }
    return sum;
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float defocus=params.x,lights=params.y,hex=step(.5,u_toggle);
    // Slight shift of the background itself, less than either disc layer.
    vec2 shift=tilt*vec2(1.0,1.0)*.012;
    // Blur measured in screen pixels, so it looks the same on any photo resolution.
    float pxPerScreen=fwidth(uv.y)*u_imageSize.y*u_coverScale.y/u_zoom;
    float lod=log2(max(1.0,pxPerScreen))+mix(2.5,5.0,defocus);
    // Four rotated taps at that mip smooth out the blocky bilinear look of a single coarse sample.
    vec2 o=exp2(lod)*.5/u_imageSize*u_zoom/u_coverScale;
    vec2 su=uv-shift;
    vec3 blurred=(photo(su+vec2(o.x,o.y*.4),lod)+photo(su+vec2(-o.x*.4,o.y),lod)
                 +photo(su-vec2(o.x,o.y*.4),lod)+photo(su-vec2(-o.x*.4,o.y),lod))*.25;
    // Defocused scenes lose a little midtone contrast; keep blacks deep so discs read.
    vec3 bg=blurred*mix(.92,.78,lights);

    vec2 p=(uv-.5)*vec2(u_aspect,1.0);
    vec3 far=bokehLayer(p-tilt*.028,mix(15.0,11.0,defocus),11.0,defocus,lights,hex,.55);
    vec3 near=bokehLayer(p-tilt*.075,mix(8.0,5.5,defocus),37.0,defocus,lights,hex,.75);
    vec3 glow=(far+near)*mix(.55,1.0,lights);
    // Screen blend: light adds visibly on dark areas and fades into already bright ones, like film.
    glow=glow/(1.0+glow*.6);
    return 1.0-(1.0-bg)*(1.0-min(glow,vec3(1.0)));
}
