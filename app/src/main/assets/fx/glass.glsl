// Liquid Glass: drops of clear liquid glass float on a frosted pane over the photo. They drift on their own, roll
// with the tilt and merge where they touch (a metaball field), and a touch pours a new drop that spreads, then
// slowly thins out. Through a drop the photo is sharp and refracted like the interface's glass: barely bent in the
// middle, pulled hard at the rim, with the colours bent apart a little; the rim brightens at grazing angles and
// catches a highlight from the light the tilt moves, and each drop casts a soft shadow on the pane.
// Sliders: frost (how milky the pane around the drops is), refraction (how strongly the drops bend light).

float lgField(vec2 p,out vec2 grad){
    float f=0.0;grad=vec2(0.0);float t=u_time*u_motion;
    vec2 roll=u_tilt*u_motion*vec2(.16,.12);
    for(int i=0;i<5;i++){
        float k=float(i);
        vec2 base=vec2((.15+.7*fract(.23+k*.311))*u_aspect,.1+.8*fract(.17+k*.437));
        vec2 c=base+.07*vec2(sin(t*(.21+.05*k)+k*1.7),cos(t*(.17+.04*k)+k*2.3))+roll*(.7+.15*k);
        float r=.068+.03*fract(k*.71);
        vec2 d=p-c;float q=dot(d,d)+1e-4;float w=r*r/q;f+=w;grad+=-2.0*w/q*d;
    }
    // The drop a touch pours: it spreads quickly, then thins out over a few seconds.
    if(u_touch.w>.5&&u_touch.z<8.0){
        vec2 c=u_touch.xy*vec2(u_aspect,1.0);c.y=1.0-c.y;
        float r=.13*(1.0-exp(-u_touch.z*4.0))*exp(-u_touch.z*.18);
        vec2 d=p-c;float q=dot(d,d)+1e-4;float w=r*r/q;f+=w;grad+=-2.0*w/q*d;
    }
    return f;
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float frost=params.x,bend=params.y;
    vec2 p=vec2(uv.x*u_aspect,uv.y);
    vec2 grad;float f=lgField(p,grad);
    // Inside a drop where the field passes 1; its "height" rises over the rim and is flat in the body.
    float inside=smoothstep(.96,1.04,f);
    float h=smoothstep(1.0,2.4,f);
    vec2 n=-grad/max(length(grad),1e-4);              // outward normal of the drop's outline
    float slope=(1.0-h)*clamp(length(grad)*.12,0.0,1.0);  // no rim where two drops meet and the surface is flat

    // The pane around the drops: frosted, with the drops' soft shadows thrown away from the light.
    vec2 light=normalize(vec2(-.45,.85)-tilt*.6);
    vec3 pane=photo(uv,frost*3.2);
    pane=mix(pane,vec3(dot(pane,vec3(.333))),frost*.12)+vec3(.02)*frost;
    vec2 g2;float shadowF=lgField(p+light*vec2(.018,.018),g2);
    pane*=1.0-.22*smoothstep(.85,1.1,shadowF)*(1.0-inside);

    // Through a drop: sharp, magnified a touch in the body, pulled in at the rim, the colours bent apart.
    float k=(.015+.05*bend)*u_motion;
    vec2 off=(-n*slope*slope*k)/vec2(u_aspect,1.0);
    vec2 centreish=(uv-.5)*.06*h*bend;               // a gentle lens in the flat body
    vec2 q=uv+off-centreish;
    float disp=.12+.18*bend;
    vec3 drop=vec3(photo(q+off*disp,0.0).r,photo(q,0.0).g,photo(q-off*disp,0.0).b);
    // Fresnel rim, the highlight facing the light, a faint bounce opposite, a focused glow inside the far side.
    float facing=dot(n,light);
    drop+=vec3(.9,.95,1.0)*pow(slope,2.5)*.14;
    drop+=vec3(1.0)*pow(max(facing,0.0),7.0)*pow(slope,1.4)*.7;
    drop+=vec3(.75,.85,1.0)*pow(max(-facing,0.0),6.0)*pow(slope,2.0)*.18;
    drop+=vec3(1.0,.97,.9)*smoothstep(.5,1.0,h)*max(-facing,0.0)*.06;

    return mix(pane,drop,inside);
}
