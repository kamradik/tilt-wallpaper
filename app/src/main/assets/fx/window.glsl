// Window: the photo sits at the back of a deep recess cut into a dark wall. Rays from a viewer in
// front of the screen enter the opening and either reach the photo or hit the recess walls first.

float windowBox(vec2 p,vec2 hs,float r){
    vec2 q=abs(p)-hs+r;
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
}
vec2 windowNormal(vec2 p,vec2 hs,float r){
    vec2 q=abs(p)-hs+r;
    vec2 g=q.x>0.0&&q.y>0.0?normalize(q):(q.x>q.y?vec2(1.0,0.0):vec2(0.0,1.0));
    return g*sign(p+1e-6);
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float aspect=u_aspect;
    vec2 p=(uv-.5)*vec2(aspect,1.0);
    float margin=mix(.035,.13,params.y)*min(1.0,aspect*2.0);
    vec2 hs=vec2(aspect*.5,.5)-margin;
    float radius=min(.05,min(hs.x,hs.y)*.4);
    float depth=mix(.05,.2,params.x);
    float aa=max(fwidth(p.y),1e-4);

    // Viewer: a central eye (so the recess reads as deep even flat) displaced by the tilt.
    vec2 view=p*.7+tilt*vec2(1.0,1.0)*1.1;
    // Light from the upper left, swinging with the tilt.
    vec2 light2=normalize(vec2(-.55,.85)-tilt*.9);
    vec3 L=normalize(vec3(light2,1.1));

    float sdf0=windowBox(p,hs,radius);
    vec3 wallTone=photo(uv,7.0);
    float wallLum=dot(wallTone,vec3(.2126,.7152,.0722));
    // Material of the recess and frame: the photo's own average hue, quiet and dark.
    vec3 material=mix(vec3(wallLum),wallTone,.55)*.55+.05;

    // ---- outside the opening: a rounded moulding round the edge, then the wall face
    vec3 wall=photo(uv,6.5);
    wall=mix(vec3(dot(wall,vec3(.2126,.7152,.0722))),wall,.7)*.26+.012;
    wall*=.8+.4*clamp(dot(p,light2)+.5,0.0,1.0);
    float mouldW=.024;
    float mt=clamp(sdf0/mouldW,0.0,1.0);
    vec2 n2=windowNormal(p,hs,radius);
    // Half-round profile: its normal sweeps from facing the opening to facing the wall.
    float sx=mt*2.0-1.0;
    vec3 mn=normalize(vec3(n2*sx,sqrt(max(0.0,1.0-sx*sx))+.15));
    float diffuse=clamp(dot(mn,L),0.0,1.0);
    vec3 moulding=material*(.35+.95*diffuse);
    if(u_toggle>.5){
        vec3 H=normalize(L+vec3(0.0,0.0,1.0));
        float spec=pow(clamp(dot(mn,H),0.0,1.0),60.0);
        moulding+=vec3(1.0,.97,.92)*spec*.8;
    }
    // The moulding throws a soft shadow onto the wall, away from the light.
    float wallSdf=sdf0-mouldW;
    float shade=windowBox(p+light2*.03,hs,radius)-mouldW;
    wall*=1.0-.45*exp(-max(wallSdf,0.0)*28.0)*(.5+.5*smoothstep(.03,-.01,shade));
    vec3 frame=mix(moulding,wall,smoothstep(-aa,aa,wallSdf));

    // ---- inside the opening: march the ray back until it either hits a wall or the photo plane
    vec2 back=p+view*depth;
    float sdfBack=windowBox(back,hs,radius);
    // Convex opening: if the far end is inside, the whole ray is. Otherwise bisect for the wall hit.
    float lo=0.0,hi=depth;
    for(int i=0;i<7;i++){
        float mid=(lo+hi)*.5;
        if(windowBox(p+view*mid,hs,radius)>0.0)hi=mid;else lo=mid;
    }
    float zHit=hi;
    vec2 hitP=p+view*zHit;
    vec2 wn=windowNormal(hitP,hs,radius);
    float zf=zHit/depth;
    // Wall facing: walls whose inward normal faces the light are lit.
    float facing=clamp(dot(-wn,light2)*.6+.5,0.0,1.0);
    vec3 recess=material*(.35+.85*facing);
    // Darker the deeper; a soft crease where the wall meets the photo plane.
    recess*=mix(1.0,.45,zf)*(1.0-.35*smoothstep(.85,1.0,zf));

    // Photo at the back: fills the opening with a little slack for parallax.
    vec2 puv=back/(2.0*hs)*.94+.5;
    vec3 pic=photo(puv,0.0);
    // Ambient occlusion along the back edge and the shadow the opening casts from the light.
    float inner=-sdfBack;
    pic*=mix(.5,1.0,smoothstep(0.0,.07,inner));
    float shadowEdge=-windowBox(back-light2*depth*.45,hs,radius);
    pic*=mix(.62,1.0,smoothstep(-.01,.03,shadowEdge));

    float seePhoto=1.0-smoothstep(-aa*1.5,aa*1.5,sdfBack);
    vec3 inside=mix(recess,pic,seePhoto);

    float open=1.0-smoothstep(-aa,aa,sdf0);
    return mix(frame,inside,open);
}
