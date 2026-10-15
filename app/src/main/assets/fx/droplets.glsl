// Droplets: Droplets.java slides rain drops over fogged glass along real gravity. Each cell knows the drop standing
// tallest over it (centre offset in RG, radius in B), so a 3x3 lookup draws exact round lenses at any size; A is how
// clear the fog is along the trails. Each drop refracts a sharp, flipped piece of the photo behind it.

const vec2 DR_FIELD=vec2(72.0,160.0);

bool drLive(){vec4 m=texelFetch(u_field,ivec2(71,159),0);return m.a>.99&&m.r+m.g+m.b<.01;}

// Previews get an empty field: scatter small drops and draw a couple of trails instead.
vec4 drCell(ivec2 q,bool live){
    if(live)return texelFetch(u_field,q,0);
    vec2 h=hash2(vec2(q)+.71);
    if(h.x>.07)return vec4(128.0/255.0,128.0/255.0,0.0,0.0);
    vec2 off=(hash2(vec2(q)*1.7+3.1)-.5)*.5;
    float r=.3+.65*h.y*h.y/.07*h.x;
    return vec4((128.0+off*16.0)/255.0,r*32.0/255.0,0.0);
}
float drClear(vec2 uv,bool live){
    if(live)return texture(u_field,uv).a;
    float t1=exp(-pow((uv.x-.31-.01*sin(uv.y*20.0))*70.0,2.0))*step(uv.y,.78);
    float t2=exp(-pow((uv.x-.68-.012*sin(uv.y*15.0+1.0))*60.0,2.0))*step(uv.y,.55);
    return max(t1,t2);
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float drops=params.x,fog=params.y;
    bool live=drLive();
    float k=72.0/(160.0*u_aspect);
    vec2 g=uv*DR_FIELD;
    ivec2 c=ivec2(floor(g));
    float aa=max(fwidth(g.x),1e-3)*.75;
    vec3 L=normalize(vec3(-.35+tilt.x*.9,.55+tilt.y*.9,.8));

    // Fogged glass: a soft, lifted, slightly cool copy of the photo, with a faint bead texture.
    float lod=mix(2.2,5.6,fog);
    vec3 fogged=(photo(uv+vec2(.004,.002),lod)+photo(uv-vec2(.002,.004),lod))*.5;
    fogged=mix(fogged,fogged*.72+vec3(.19,.21,.24),fog*.85);
    vec2 bh=hash2(floor(uv*vec2(500.0,500.0/u_aspect)));
    fogged*=1.0+(bh.x-.5)*.05*fog;
    vec3 sharp=photo(uv,0.0);
    float clear=smoothstep(.04,.85,drClear(uv,live));
    // Trails are clear glass with a faintly darker wet edge.
    vec3 col=mix(fogged,sharp,clear);
    col*=1.0-.08*clear*(1.0-clear)*4.0*fog;

    // Find the tallest visible drop over this pixel, and the strongest shadow of a drop beside it.
    float thr=mix(2.4,-.4,drops);
    float best=0.0,shade=0.0;vec2 bv=vec2(0.0);float br=1.0,bvis=0.0;
    for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){
        ivec2 q=c+ivec2(i,j);
        if(q.x<0||q.y<0||q.x>71||q.y>159||q==ivec2(71,159))continue;
        vec4 f=drCell(q,live);
        float r=f.b*(255.0/32.0);
        if(r<.05)continue;
        float vis=smoothstep(thr,thr+.35,r);
        if(vis<=0.0)continue;
        vec2 centre=vec2(q)+.5+(f.rg*255.0-128.0)/16.0;
        vec2 v=(g-centre)*vec2(1.0,k);
        float d=length(v);
        float cap=(r+aa)*(r+aa)-d*d;
        if(cap>0.0&&cap*vis>best){best=cap*vis;bv=v;br=r;bvis=vis;}
        // A soft shadow and lens glow on the glass, offset away from the light.
        float sd=length(v+L.xy*r*.25);
        shade=max(shade,vis*smoothstep(r*1.35,r*.9,sd));
    }
    col*=1.0-.22*shade*(.4+.6*fog);
    if(best>0.0){
        float d=length(bv);
        float cover=(1.0-smoothstep(br-aa,br+aa,d))*bvis;
        vec2 n=bv/br;
        float nn=min(dot(n,n),1.0),rr=sqrt(nn);
        float z=sqrt(1.0-nn);
        // A convex drop is a tiny lens: it shows a sharp, flipped and shrunken view of what lies behind it.
        vec2 off=-n*br*2.6;
        vec2 suv=uv+vec2(off.x/DR_FIELD.x,off.y/k/DR_FIELD.y);
        vec3 inner=photo(suv,0.0)*1.04;
        // The rim bends light away (a dark outline); the side facing away from the light gathers a caustic.
        inner*=1.0-.7*smoothstep(.62,1.0,rr);
        float away=clamp(dot(-n,normalize(L.xy+1e-4)),0.0,1.0);
        inner+=vec3(1.0,.98,.94)*smoothstep(.45,.9,rr)*smoothstep(1.0,.85,rr)*away*away*.35;
        vec3 N=normalize(vec3(n*1.2,z));
        vec3 H=normalize(L+vec3(0.0,0.0,1.0));
        float nh=max(dot(N,H),0.0);
        float spec=pow(nh,140.0)*1.6+pow(nh,18.0)*.06;
        // A thin bright rim on the lit side, like the meniscus catching the light.
        float rim=smoothstep(.8,.97,rr)*smoothstep(1.02,.95,rr)*clamp(dot(n,normalize(L.xy+1e-4)),0.0,1.0)*.35;
        vec3 drop=inner+vec3(1.0,.99,.97)*(spec+rim);
        col=mix(col,drop,cover);
    }
    return col;
}
