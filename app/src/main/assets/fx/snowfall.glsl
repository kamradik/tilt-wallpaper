// Snowfall: flakes fall at several depths through the scene. Each layer of flakes sits at its own depth, so it
// passes in front of what is farther and hides behind what is nearer (the subject stands in the snow, not under
// a sticker of it). The nearest flakes are big and out of focus. Snow settles on the top edges of things, where the
// depth map steps from far above to near below, and builds up over the first half minute after the screen comes
// on; a shake knocks it off. Gravity sets the direction of the fall.
// Sliders: snow (how thick it falls), drifts (how much settles).

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float amount=params.x,drifts=params.y;float t=u_time*u_motion;
    vec2 fu=uv-tilt*.006;
    vec3 col=photo(fu,0.0);
    float depth=texture(u_depth,coords(fu)).r;
    // Winter light: a touch cooler and lifted.
    col=mix(col,dot(col,vec3(.299,.587,.114))*vec3(.92,.97,1.06),.25)+vec3(.015,.02,.03);

    // Settled snow on upward-facing edges: nearer here than just above, so something's top is here.
    vec2 up=vec2(0.0,.012);
    float step1=depth-texture(u_depth,coords(fu+up)).r,step2=depth-textureLod(u_depth,coords(fu+up*2.5),1.0).r;
    float ledge=smoothstep(.04,.16,max(step1,step2*.8));
    float settle=smoothstep(0.0,30.0,u_wake.x)*(1.0-smoothstep(.15,.6,u_gravity.z));
    float grain=hash2(floor(uv*vec2(u_aspect,1.0)*300.0)).x;
    float cap=ledge*settle*drifts*(.7+.3*grain);
    col=mix(col,vec3(.95,.97,1.0)-.06*grain,clamp(cap,0.0,.9));

    // Falling flakes in four layers, far to near; the fall follows gravity with a little sway.
    vec2 g=length(u_gravity.xy)>.15?normalize(vec2(u_gravity.x,u_gravity.y)):vec2(0.0,-1.0);
    vec2 p=vec2(uv.x*u_aspect,uv.y);
    for(int l=0;l<4;l++){
        float k=float(l),z=.2+k*.22;                     // the layer's depth: far .2 .. near .86
        float scale=mix(26.0,7.0,k/3.0),speed=mix(.05,.16,k/3.0);
        vec2 q=p*scale-g*t*speed*scale+vec2(sin(t*.6+k*2.0)*.4,0.0)+tilt*(1.0+k)*.6;
        vec2 cell=floor(q),f=fract(q)-.5;vec2 h=hash2(cell+k*13.0);
        if(h.x>amount*.85+.05)continue;
        // Near flakes are big and soft, so they keep to the middle of their cell (no clipped edges).
        vec2 o=(h-.5)*(k==3.0?.16:.6);float r=k==3.0?mix(.12,.18,h.y):mix(.07,.16,h.y);
        float edge=k==3.0?.14:.04;
        float flake=smoothstep(r+edge,r*.4,length(f-o));
        // Hidden where the scene is nearer than the layer.
        float behind=smoothstep(z+.06,z-.02,depth);
        col=mix(col,vec3(1.0),flake*behind*(k==3.0?.55:.85));
    }
    return col;
}
