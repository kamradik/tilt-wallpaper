// Thunderstorm: the scene darkens under heavy cloud and rain falls in slanting streaks at several depths, hidden
// behind whatever is nearer, as the snow is. Now and then lightning strikes: a jagged bolt in the far sky and a
// flash that lights the near planes more than the far ones, so for an instant the depth of the scene shows.
// Sliders: rain, lightning (how often and how bright).

float stNoise(float y,float seed){float i=floor(y),f=fract(y);float a=hash2(vec2(i,seed)).x,b=hash2(vec2(i+1.0,seed)).x;return mix(a,b,f)-.5;}
float stBolt(vec2 p,float seed){
    // A bolt from the top: a line whose x wanders in kinks at several scales, glowing, thinner toward its tip.
    float y=p.y,x=0.0;
    for(int i=0;i<5;i++){float k=float(i),s=3.0*pow(2.1,k);x+=stNoise(y*s,seed+k*9.0)*(.16/pow(1.9,k));}
    float w=.0025+.004*smoothstep(.3,1.0,y),d=abs(p.x-x);
    return (smoothstep(w,0.0,d)+exp(-d*60.0)*.35)*smoothstep(.2,.5,y);
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float rain=params.x,bolts=params.y;float t=u_time*u_motion;
    vec2 fu=uv-tilt*.006;
    vec3 base=photo(fu,0.0);
    float depth=texture(u_depth,coords(fu)).r;
    // Storm light: dark, cool, a little desaturated.
    vec3 col=mix(base,vec3(dot(base,vec3(.299,.587,.114))),.35)*vec3(.62,.68,.78);

    // Lightning: one chance every 2.5-6 s, a double flicker.
    float period=mix(6.0,2.5,bolts),n=floor(t/period),age=t-n*period;
    vec2 h=hash2(vec2(n,7.0));float strike=step(h.x,.35+.5*bolts);
    float flash=strike*(exp(-age*9.0)+.6*exp(-max(0.0,age-.18)*12.0)*step(.18,age))*(.6+.6*bolts);
    // The flash lights near things more: depth shows for an instant.
    col+=col*flash*(.25+.75*depth)*(1.0-.5*dot(base,vec3(.333)))+vec3(.55,.6,.75)*flash*(1.0-depth)*.18;
    // The bolt itself, in the far sky only.
    vec2 bp=vec2((uv.x-(.25+.5*h.y))*u_aspect,uv.y);
    float bolt=stBolt(vec2(bp.x,uv.y),n)*strike*exp(-age*6.0)*(1.0-smoothstep(.12,.3,depth));
    col+=vec3(.85,.9,1.0)*bolt*1.6;

    // Rain: streaks in three layers, slanting with the tilt and gravity, hidden behind nearer things.
    vec2 g=length(u_gravity.xy)>.15?normalize(vec2(u_gravity.x,u_gravity.y)):vec2(0.0,-1.0);
    vec2 slant=normalize(g+vec2(tilt.x*.4+.15,0.0));
    vec2 p=vec2(uv.x*u_aspect,uv.y);
    mat2 rot=mat2(-slant.y,slant.x,-slant.x,-slant.y);  // rain runs along local y
    for(int l=0;l<3;l++){
        float k=float(l),z=.25+k*.28;
        vec2 q=rot*p*vec2(mix(70.0,30.0,k/2.0),mix(6.0,3.0,k/2.0));
        q.y+=t*mix(9.0,16.0,k/2.0);
        vec2 cell=floor(q),f=fract(q);float r=hash2(cell+k*5.0).x;
        if(r>rain*.75+.1)continue;
        float streak=smoothstep(.5,.0,abs(f.x-.5)*mix(3.0,1.6,k/2.0))*smoothstep(0.0,.25,f.y)*smoothstep(1.0,.55,f.y);
        float behind=smoothstep(z+.06,z-.02,depth);
        col+=vec3(.75,.8,.9)*streak*behind*mix(.16,.32,k/2.0)*(1.0+flash);
    }
    return col;
}
