// Sand: Sand.java pours grains along real gravity on the field grid; this draws them as rounded grains lit from a
// tilt-driven light. R is occupancy (bilinear gives the heap's outline), G the grain's height in its column, BA the
// field cell the grain started in, so photo-coloured sand carries its own little piece of the picture as it pours.
// The Amount slider changes how much sand Sand.java simulates; only the preview heap below reads it here.

const vec2 SD_FIELD=vec2(72.0,160.0);

bool sdLive(){vec4 m=texelFetch(u_field,ivec2(71,159),0);return m.a>.99&&m.r+m.g+m.b<.01;}

// Previews get an empty field: stage a heap along the bottom instead, as high as the Amount slider makes it.
float sdHeap=.3;
float sdSurface(float x){return sdHeap+.045*sin(x*5.0+.6)+.02*sin(x*13.0+2.0);}
vec4 sdField(vec2 uv,bool live){
    if(live)return texture(u_field,uv);
    float s=sdSurface(uv.x),occ=1.0-smoothstep(s-.004,s+.004,uv.y);
    return vec4(occ,occ>.5?uv.y/s:1.0,0.0,0.0);
}
vec4 sdCell(ivec2 q,bool live){
    if(live)return texelFetch(u_field,q,0);
    vec2 uv=(vec2(q)+.5)/SD_FIELD;
    float s=sdSurface(uv.x);
    return uv.y<s?vec4(1.0,uv.y/s,vec2(q)/255.0):vec4(0.0,1.0,0.0,0.0);
}
// Sand that shows: occupied (cut is kept above every height, so nothing is trimmed).
float sdShown(vec2 uv,bool live,float cut){
    vec4 f=sdField(uv,live);
    return smoothstep(.3,.7,f.r)*(1.0-smoothstep(cut-.04,cut+.04,f.g));
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float amount=params.x,grainSize=params.y;
    bool live=sdLive(),fromPhoto=u_toggle>.5;
    float cut=1.04;sdHeap=mix(.08,.6,amount);
    vec3 L=normalize(vec3(-.4+tilt.x*.9,.65+tilt.y*.9,.7));
    vec2 g=uv*SD_FIELD;
    ivec2 c=ivec2(floor(g));
    vec2 sub=fract(g);

    vec3 col=photo(uv,0.0);
    vec4 f=sdField(uv,live);
    float occ=smoothstep(.3,.7,f.r);
    float shown=occ*(1.0-smoothstep(cut-.04,cut+.04,f.g));

    // The cell's grain carries the field cell it started in: grain texture and photo colour are both looked up
    // there, so sand at rest is seamless and a grain keeps its look as it moves.
    vec4 cell=sdCell(c,live);
    if(cell.r<.5){
        // Fringe pixels of the bilinear outline sit in an empty cell: borrow the nearest grain-holding neighbour.
        ivec2 sx=c+ivec2(sub.x<.5?-1:1,0),sy=c+ivec2(0,sub.y<.5?-1:1);
        vec4 a=sdCell(clamp(sx,ivec2(0),ivec2(71,159)),live),b=sdCell(clamp(sy,ivec2(0),ivec2(71,159)),live);
        cell=a.r>=b.r?a:b;
    }
    vec2 home=floor(cell.ba*255.0+.5)+sub;
    // Grains: jittered points (Worley F1/F2 over the 2x2 nearest); Grain sets how many fit in a cell.
    float n=mix(4.2,1.5,grainSize);
    vec2 tc=home*n*vec2(1.0,72.0/(160.0*u_aspect));
    vec2 base0=floor(tc-.5);
    float f1=9.0,f2=9.0;vec2 nearest=vec2(0.0),id=vec2(0.0);
    for(int j=0;j<2;j++)for(int i=0;i<2;i++){
        vec2 b=base0+vec2(float(i),float(j));
        vec2 pt=b+.2+.6*hash2(b);
        vec2 dv=tc-pt;float dd=dot(dv,dv);
        if(dd<f1){f2=f1;f1=dd;nearest=dv;id=b;}else if(dd<f2)f2=dd;
    }
    float aaG=fwidth(tc.x)+1e-3;
    vec2 h=hash2(id*1.37+.5),h2=hash2(id*.71+9.1);
    // Grainy outline: single grains stand proud of the surface or are missing from it.
    float edge=smoothstep(.15,.85,shown+(h.x-.5)*.6*(1.0-shown)*step(.02,shown));
    if(edge>.001||f.r>.05){
        vec2 e=1.0/SD_FIELD;
        float sR=sdShown(uv+vec2(e.x,0.0),live,cut),sL=sdShown(uv-vec2(e.x,0.0),live,cut);
        float sU=sdShown(uv+vec2(0.0,e.y),live,cut),sD=sdShown(uv-vec2(0.0,e.y),live,cut);
        // The heap darkens the photo right beside it, as if it stood on the glass.
        col*=1.0-.3*smoothstep(0.0,.5,(sR+sL+sU+sD)*.25)*(1.0-edge);
        if(edge<=.001)return col;
        // Each grain is a small dome; the crevices between grains are dark.
        float border=sqrt(f2)-sqrt(f1);
        float gap=smoothstep(0.0,.18+aaG,border);
        vec3 bump=normalize(vec3(nearest*1.6,.8));
        float lambert=clamp(dot(bump,L),0.0,1.0);
        vec3 baseCol;
        if(fromPhoto){
            // The grain's own patch of the picture, slightly warmed toward sand.
            baseCol=photo(home/SD_FIELD,.6);
            float l=dot(baseCol,vec3(.2126,.7152,.0722));
            baseCol=mix(baseCol,vec3(l)*vec3(1.08,.98,.82),.18)*(.9+.2*h.x);
        }else{
            baseCol=mix(vec3(.80,.60,.36),vec3(.96,.84,.62),h.x);
            if(h.y<.08)baseCol*=vec3(.6,.52,.45);
            else if(h.y>.94)baseCol=vec3(.98,.95,.9);
        }
        // Heap shape: light the outline from its gradient so the surface reads as a slope.
        vec3 hn=normalize(vec3(-(sR-sL)*1.2,-(sU-sD)*1.2,1.0));
        float slope=.8+.3*clamp(dot(hn,L),0.0,1.0);
        float relief=fromPhoto?.45:.75;// photo sand keeps more of the picture
        vec3 sand=baseCol*mix(1.0,(.55+.65*lambert)*mix(.6,1.0,gap),relief)*slope;
        // A rare grain of quartz catches the light at just the right tilt.
        float facet=h2.x*50.0+dot(tilt,vec2(11.0,8.0));
        sand+=vec3(1.0,.96,.88)*pow(.5+.5*cos(facet),30.0)*step(.88,h2.y)*gap*smoothstep(.5,0.0,length(nearest))*.9;
        col=mix(col,sand,edge);
    }
    return col;
}
