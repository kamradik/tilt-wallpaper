// Snow Globe: SnowGlobe.java drops flakes along real gravity; this draws the drift they form (from the bilinear
// density in R) and one crisp flake per cell from its 3x3 neighbourhood (sub-cell position in GB, kind in A).
// Glitter flashes as its facets catch a light that moves with the tilt, and keeps tumbling while airborne.

const vec2 SG_FIELD=vec2(72.0,160.0);

// The simulation marks the top-right texel; previews render with an empty field and get a staged scene instead.
bool sgLive(){vec4 m=texelFetch(u_field,ivec2(71,159),0);return m.a>.99&&m.r+m.g+m.b<.01;}

vec4 sgCell(ivec2 q,bool live){
    if(live)return texelFetch(u_field,q,0);
    vec2 h=hash2(vec2(q)+.37);
    bool heap=q.y<5;
    if(h.x>(heap?.7:.045))return vec4(0.0);
    float rank=1.0+floor(fract(h.x*91.7+h.y*13.1)*63.0);
    float glitter=fract(h.y*37.3)<.22?128.0:0.0;
    return vec4(0.0,h.y,fract(h.x*7.31+.2),(rank+glitter+(heap?0.0:64.0))/255.0);
}
float sgDensity(vec2 uv,bool live){
    if(live)return texture(u_field,uv).r*(255.0/160.0);
    float surface=.032+.008*sin(uv.x*17.0+1.3)+.005*sin(uv.x*41.0);
    return 1.2*(1.0-smoothstep(surface-.012,surface+.012,uv.y));
}

vec3 sgGlitterColor(float rank,bool colour){
    if(!colour)return vec3(.92,.94,1.0);
    float h=fract(rank*.618034);
    // Pastel iridescent hues (gold, rose, mint, lilac, ice), never neon.
    vec3 c=.55+.45*cos(6.2832*(h+vec3(0.0,.33,.67)));
    return mix(vec3(1.0,.86,.55),c,.75);
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float amount=params.x,glitter=params.y;
    bool live=sgLive(),colour=u_toggle>.5;
    float k=72.0/(160.0*u_aspect);// cell height in cell widths
    vec2 g=uv*SG_FIELD;
    ivec2 c=ivec2(floor(g));
    float aa=max(fwidth(g.x),1e-3)*.8;
    vec3 L=normalize(vec3(-.35+tilt.x*.8,.6+tilt.y*.8,.75));

    // The photo behind thick curved glass: a hint of barrel magnification and a cool vignette.
    vec2 p=(uv-.5)*vec2(u_aspect,1.0);
    vec2 bulge=uv-(uv-.5)*dot(p,p)*.06;
    vec3 col=photo(bulge,0.0);
    float vig=smoothstep(.95,.25,length(p*vec2(1.25,1.0)));
    col*=mix(vec3(.82,.86,.95),vec3(1.0),vig);

    // Drift: the settled snow, thinner with a lower Amount. Four diagonal taps smooth the cell-sized density into
    // soft mounds and give the slope that lights them.
    vec2 e=.75/SG_FIELD;
    float d1=sgDensity(uv+vec2(e.x,e.y),live),d2=sgDensity(uv+vec2(-e.x,e.y),live);
    float d3=sgDensity(uv+vec2(e.x,-e.y),live),d4=sgDensity(uv+vec2(-e.x,-e.y),live);
    float d=(d1+d2+d3+d4)*.25;
    vec2 gh=hash2(floor(uv*vec2(420.0,420.0/u_aspect)));
    float thr=mix(.95,.4,amount);
    float edgeAA=max(fwidth(d),1e-3);
    float drift=smoothstep(thr-.08-edgeAA,thr+.08+edgeAA,d);
    if(d>thr-.4){
        vec3 n=normalize(vec3(-(d1+d3-d2-d4)*.9,-(d1+d2-d3-d4)*.9*k,1.0));
        float lit=clamp(dot(n,L),0.0,1.0);
        // Deeper snow is brighter; the thin rim takes the cool shadow colour.
        vec3 snow=mix(vec3(.74,.80,.92),vec3(1.0,.995,.985),.35+.65*lit);
        snow*=.93+.07*smoothstep(thr,thr+.5,d);
        snow*=.99+.02*gh.y;
        // Soft contact shade on the photo just beyond the drift's edge.
        col*=1.0-.25*smoothstep(thr-.4,thr,d)*(1.0-drift);
        col=mix(col,snow,drift);
        // Ice crystals in the drift glint as the light moves.
        float facet=gh.y*40.0+dot(tilt,vec2(9.0,7.0))+gh.x*6.2832;
        float glint=pow(.5+.5*cos(facet),40.0)*step(.93,gh.x)*drift*(.35+glitter);
        col+=vec3(1.0,.98,.94)*glint*1.4;
    }

    // Flakes: one per cell, looked up in the 3x3 neighbourhood so any flake may spill into this pixel. Shadows and
    // flakes are gathered separately so a neighbour's shadow never cuts into a flake drawn before it.
    vec3 glow=vec3(0.0),flakeCol=vec3(0.0);
    float shadow=0.0,cover=0.0;
    for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){
        ivec2 q=c+ivec2(i,j);
        if(q.x<0||q.y<0||q.x>71||q.y>159||q==ivec2(71,159))continue;
        vec4 f=sgCell(q,live);
        int kind=int(f.a*255.0+.5);
        if(kind==0)continue;
        float rank=float(kind&63);
        bool isGlitter=(kind&128)!=0,air=(kind&64)!=0;
        float vis=1.0-smoothstep(-.02,.02,rank/63.0-(isGlitter?glitter:amount));
        // Flakes resting in the drift are part of its surface; only loose ones are drawn on top.
        if(!air)vis*=1.0-drift;
        if(vis<=0.0)continue;
        vec2 centre=vec2(q)+(f.gb*255.0+.5)/256.0;
        vec2 v=(g-centre)*vec2(1.0,k);
        float dist=length(v);
        vec2 h=hash2(vec2(rank,isGlitter?7.0:3.0));
        if(!isGlitter){
            // Most flakes are crisp; a few are near the lens, larger and out of focus.
            bool near=h.y>.86&&air;
            float r=near?.98:mix(.4,.68,h.x);// within one cell: the 3x3 lookup must see all of it
            float a=(1.0-smoothstep(r*(near?.2:.4)-aa,r+aa,dist))*vis*(near?.42:1.0);
            // Lit from the tilt light: a bright face and a cool edge, so flakes read on pale photos too.
            float facing=clamp(dot(v/max(r,1e-3),L.xy)*.5+.5,0.0,1.0);
            vec3 flake=near?vec3(1.0):mix(vec3(.84,.88,.96),vec3(1.0),smoothstep(r,r*.3,dist)*.75+facing*.25);
            if(!near)shadow=max(shadow,vis*(1.0-smoothstep(r*.5,r*1.2,length(v+L.xy*r*.2))));
            if(a>cover){cover=a;flakeCol=flake;}
        }else{
            // Flat glitter: a small hexagon-ish plate that flashes, with a four-point star at its peak.
            float r=mix(.3,.42,h.x);
            vec2 a2=abs(v);
            float plate=max(a2.x*.866+a2.y*.5,a2.y);
            float body=(1.0-smoothstep(r-aa,r+aa,plate))*vis;
            float phase=rank*2.39+dot(tilt,vec2(cos(rank*1.3),sin(rank*1.7)))*10.0+(air?u_time*5.0:0.0);
            float flash=pow(.5+.5*cos(phase),8.0);
            vec3 tint=sgGlitterColor(rank,colour);
            if(body>cover){cover=body;flakeCol=tint*(.82+.25*h.y)+flash*.4;}
            float star=(exp(-a2.x*16.0)*exp(-a2.y*2.6)+exp(-a2.y*16.0)*exp(-a2.x*2.6))*smoothstep(1.0,.45,dist);
            glow+=(tint*.6+.4)*(flash*(body*1.4+star*1.1*vis));
        }
    }
    col*=1.0-.2*shadow;
    col=mix(col,flakeCol,cover)+glow;

    // A faint curved reflection on the globe that slides as you tilt.
    vec2 rp=p-vec2(.28,.36)-tilt*vec2(.06,.05);
    float arc=abs(length(rp*vec2(1.0,.8))-.42);
    float sheen=exp(-arc*arc*900.0)*smoothstep(.1,-.25,rp.y+rp.x*.4)*smoothstep(-.6,-.2,rp.x);
    col+=vec3(.9,.95,1.0)*sheen*.07;
    return col;
}
