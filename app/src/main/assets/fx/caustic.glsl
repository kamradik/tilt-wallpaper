// Submerged: the photo lies on the floor of a sunlit pool. Waves on the surface bend the view of it, focus the
// sunlight into the thin, shifting net of caustics that dances over it, and the water absorbs red with depth.
// Tilting moves the sun: the caustic net slides and the shafts of light lean. Sliders: caustics, water depth.
// Switch: ripples (surface refraction and sun glints on the wavelets).

// The surface: four travelling waves. Returns the height and its gradient.
vec3 uwSurface(vec2 p,float t){
    vec3 s=vec3(0.0);
    vec2 d[4];d[0]=normalize(vec2(1.0,.35));d[1]=normalize(vec2(-.6,1.0));d[2]=normalize(vec2(.25,-1.0));d[3]=normalize(vec2(-1.0,-.2));
    float f[4];f[0]=9.0;f[1]=13.0;f[2]=21.0;f[3]=31.0;
    float a[4];a[0]=.45;a[1]=.3;a[2]=.17;a[3]=.08;
    for(int i=0;i<4;i++){
        float ph=dot(d[i],p)*f[i]-t*(1.1+.35*float(i));
        s.x+=a[i]*sin(ph);s.yz+=a[i]*f[i]*cos(ph)*d[i];
    }
    return s;
}
// Distance to the nearest edge of a drifting cell pattern (Worley F2 - F1): small along the edges.
float uwEdge(vec2 p,float t){
    vec2 i=floor(p),f=fract(p);float f1=8.0,f2=8.0;
    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
        vec2 n=vec2(float(x),float(y)),h=hash2(i+n);
        vec2 o=.5+.38*sin(t*(.6+.4*h)+6.2832*h);
        float d=length(n+o-f);
        if(d<f1){f2=f1;f1=d;}else if(d<f2)f2=d;
    }
    return f2-f1;
}
// Caustic brightness at p: two drifting nets of thin bright lines, brighter where they cross.
float uwCaustic(vec2 p,float t,float soft){
    vec2 w=p+vec2(sin(p.y*1.3+t*.45),cos(p.x*1.1-t*.4))*.42+vec2(sin(p.y*3.1-t*.7),cos(p.x*2.7+t*.6))*.12;
    float e1=uwEdge(w*1.0,t),e2=uwEdge(w*1.55+3.7,t*1.3);
    float k=mix(.055,.13,soft);
    float c1=1.0-smoothstep(0.0,k,e1),c2=1.0-smoothstep(0.0,k*1.15,e2);
    return c1*c1*.7+c2*c2*.45+c1*c2*1.1;
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float strength=params.x,depth=params.y;
    bool ripples=u_toggle>.5;
    float t=u_time;
    vec2 q=(uv-.5)*vec2(u_aspect,1.0);

    // Refraction: the surface slope shifts what is seen on the floor, more through deeper water.
    vec3 s=uwSurface(q*1.3+tilt*.2,t*.9);
    vec2 bend=ripples?s.yz*.0011*(.6+depth):vec2(0.0);
    vec3 floorCol=photo(uv+bend,depth*1.2);

    // Water colour: red is absorbed first, so deep water goes teal and darker; the water also glows faintly.
    vec3 absorb=exp(-vec3(.62,.22,.15)*(.2+depth*.9));
    vec3 col=floorCol*mix(vec3(1.0),absorb,.5)+vec3(.008,.03,.04)*(.4+depth);

    // Caustics: the sun's direction (tilt) slides the net; deeper water spreads it wider and softer.
    float scale=mix(5.2,3.4,depth);
    vec2 cp=q*scale+tilt*vec2(-1.1,.9)+s.yz*.012;
    float c=uwCaustic(cp,t*.55,depth);
    // A hint of dispersion: the edge of each line splits warm/cool very slightly.
    float cr=uwCaustic(cp+vec2(.035,0.0),t*.55,depth);
    vec3 light=vec3(1.0,.97,.86);
    vec3 caustic=vec3(mix(c,cr,.5),c,mix(c,c*.9+.1*cr,.5))*light;
    // Caustics are focused sunlight, so they multiply the floor's colour: lines brighten it in its own hue and the
    // spaces between them, which lost that light, are a little darker. A small additive part keeps lines visible
    // on dark floors.
    float k=strength*mix(1.0,.7,depth);
    col*=mix(vec3(1.0),vec3(.8)+caustic*.95,k);
    col+=caustic*.06*k;

    // Sun shafts: broad soft bands leaning with the tilt, fading toward the bottom of the screen.
    float lean=q.x+q.y*(.35+tilt.x*.6);
    float shafts=pow(.5+.5*sin(lean*7.0+t*.25+sin(lean*2.3-t*.2)*1.4),6.0);
    col+=vec3(.55,.8,.85)*shafts*.07*(.4+.6*uv.y)*(.5+.5*strength);

    // Glints: wavelets catch the sun, sparse and sharp.
    if(ripples){
        vec3 n=normalize(vec3(-s.yz*.022,1.0));
        vec3 sun=normalize(vec3(tilt.x*.8+.25,tilt.y*.8+.45,1.0));
        vec3 h=normalize(sun+vec3(0.0,0.0,1.0));
        float g=pow(max(dot(n,h),0.0),260.0);
        col+=vec3(1.0,.98,.92)*g*.55*(.4+.6*strength);
    }
    // The pool deepens toward the edges of the view.
    col*=1.0-.18*smoothstep(.35,.85,length(q*vec2(1.0/u_aspect,1.0)*.9));
    return col;
}
