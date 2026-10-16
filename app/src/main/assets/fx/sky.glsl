// Living Sky: the depth network's map finds the sky (the farthest, upper, bright part of the picture that is not
// the subject) and the sky comes alive there, while the rest of the photo stays real and is lit to match.
//   day:    the photo's own sky, deepened toward its zenith colour, with clouds drifting over it;
//   sunset: a warm gradient with a low sun, pink-lit clouds and a golden grade on the land;
//   night:  a deep blue sky with twinkling stars, a faint Milky Way and moonlit clouds; the land goes moonlit blue.
// The clouds lie on a plane over the horizon (they shrink and crowd toward it) and drift on their own; the sky shifts
// a little with the tilt, more than the land, because it is farther away.
// Sliders: clouds (amount and speed), day <-> night (0 day, .5 sunset, 1 night). Switch: follow the clock instead.

float skHash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float skNoise(vec2 p){
    vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(skHash(i),skHash(i+vec2(1,0)),f.x),mix(skHash(i+vec2(0,1)),skHash(i+vec2(1,1)),f.x),f.y);
}
float skFbm(vec2 p){
    float v=0.0,a=.5;mat2 r=mat2(.8,-.6,.6,.8);
    for(int i=0;i<5;i++){v+=a*skNoise(p);p=r*p*2.03+vec2(1.7,9.2);a*=.5;}
    return v;
}
float skLuma(vec3 c){return dot(c,vec3(.2126,.7152,.0722));}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float amount=params.x;
    // Mood 0 day, .5 sunset, 1 night: from the slider, or from the sun's height by the clock.
    float mood=params.y;
    if(u_toggle>.5){
        float elev=-cos(fract(u_day)*6.28318);
        mood=mix(0.0,.5,smoothstep(.42,.08,elev));
        mood=mix(mood,1.0,smoothstep(-.04,-.3,elev));
    }
    float wDay=1.0-smoothstep(0.0,.5,mood),wNight=smoothstep(.5,1.0,mood),wSun=1.0-wDay-wNight;

    // The land, with a touch of parallax against the sky.
    vec2 fu=uv-tilt*.006;
    vec3 base=photo(fu,0.0);float luma=skLuma(base);

    // Where the sky is: far (depth near black), in the upper part, not the subject, and bright or blue.
    vec4 dm=texture(u_depth,coords(fu));
    float d=max(dm.r,.6*textureLod(u_depth,coords(fu),2.0).r);
    float far=1.0-smoothstep(.09,.22,d);
    float subject=dm.b>.9?dm.g:0.0;
    float high=smoothstep(.18,.55,uv.y);
    float tone=clamp(smoothstep(.18,.5,luma)+smoothstep(-.02,.12,base.b-base.r),0.0,1.0);
    float sky=smoothstep(.12,.55,far*high*(1.0-subject)*mix(.06,1.0,tone*tone));

    // Sky coordinates: aspect-correct, shifted by the tilt (far away, so more than the land), height above the skyline.
    vec2 p=(uv-.5)*vec2(u_aspect,1.0)+tilt*vec2(.05,.035);
    float hy=clamp((uv.y-.3)/.7,0.0,1.0);
    float t=u_time;

    // Day: the photo's own sky, deepened toward the colour at the top of its frame.
    vec3 top=photo(vec2(uv.x,.97),5.0);
    vec3 zen=mix(vec3(.16,.4,.86),top,.5),hor=mix(vec3(.74,.85,.97),top*1.2,.25);
    vec3 dayS=mix(mix(hor,zen,pow(hy,.8)),base,.5);
    // Sunset: violet zenith, rose middle, amber horizon, a low sun with a wide glow.
    vec3 sunS=mix(vec3(1.0,.66,.32),vec3(.86,.42,.44),smoothstep(0.0,.45,hy));
    sunS=mix(sunS,vec3(.16,.17,.42),smoothstep(.35,1.0,hy));
    vec2 sunP=vec2(.18*u_aspect,.07+.03*sin(t*.05));
    float sd=length(p-sunP);
    sunS+=vec3(1.0,.62,.3)*exp(-sd*3.2)*.55+vec3(1.0,.93,.75)*smoothstep(.04,.03,sd);
    // Night: deep blue, lighter at the horizon, a faint Milky Way band and two layers of twinkling stars.
    vec3 nightS=mix(vec3(.045,.07,.16),vec3(.008,.014,.05),smoothstep(0.0,.8,hy));
    float ca=cos(t*.004),sa=sin(t*.004);vec2 sp=mat2(ca,-sa,sa,ca)*p;
    float band=exp(-pow(dot(sp,normalize(vec2(.55,-.84)))*3.2,2.0));
    nightS+=vec3(.2,.17,.3)*band*smoothstep(.35,.75,skFbm(sp*5.0))*1.2+vec3(.05,.05,.09)*band;
    vec3 stars=vec3(0.0);
    for(int l=0;l<2;l++){
        float scale=l==0?110.0:55.0;vec2 q=sp*scale;vec2 cell=floor(q);vec2 h=hash2(cell+float(l)*31.0);
        if(h.x<(l==0?.08:.025)){
            vec2 c=cell+.2+.6*hash2(cell*1.3+7.0);float r=length(q-c),size=l==0?.07:.11;
            float tw=.65+.35*sin(t*(1.5+3.0*h.y)+h.x*40.0);
            stars+=mix(vec3(.7,.82,1.0),vec3(1.0,.9,.78),h.y)*(smoothstep(size+fwidth(q.x),size*.3,r)+exp(-r*r*16.0)*.3)*tw*pow(fract(h.y*17.3),2.0)*1.6;
        }
    }
    nightS+=stars*(.4+.6*band+.6)*smoothstep(.05,.3,hy);
    vec3 skyCol=dayS*wDay+sunS*wSun+nightS*wNight;

    // Clouds on a plane above the horizon: they shrink and crowd toward it and drift sideways and toward the viewer.
    float persp=1.0/(hy*1.1+.16);
    vec2 cq=vec2(p.x*persp*.9,persp*1.4)+vec2(t*.018,t*.006)*(.35+amount);
    float c=skFbm(cq);
    float cover=mix(.6,.34,amount);
    float cloud=smoothstep(cover,cover+.18,c)*smoothstep(.0,.18,hy);
    // Light from above (and from the sun at sunset): the side facing it is brighter.
    float lit=clamp(.55+(c-skFbm(cq+vec2(.08,.12)))*4.0,0.0,1.0);
    vec3 dayC=mix(vec3(.72,.76,.84),vec3(1.0),lit);
    vec3 sunC=mix(vec3(.38,.24,.36),vec3(1.0,.6,.44),lit)+vec3(.35,.18,.05)*exp(-sd*2.5);
    vec3 nightC=mix(vec3(.04,.05,.08),vec3(.2,.23,.32),lit);
    vec3 cloudCol=dayC*wDay+sunC*wSun+nightC*wNight;
    skyCol=mix(skyCol,cloudCol,cloud*mix(.85,.75,wNight));

    // The land, lit by the same sky: golden at sunset (brighter toward the sun), moonlit blue at night.
    vec3 gold=pow(max(base,0.0),vec3(.92,.99,1.14))*vec3(1.12,.96,.78)*.9;
    gold+=vec3(.22,.1,.03)*exp(-length(p-sunP)*2.2)*(1.0-luma);
    vec3 moon=(mix(vec3(luma),base,.35)*vec3(.42,.55,.9))*.62+vec3(.004,.008,.02);
    vec3 land=base*wDay+gold*wSun+moon*wNight;

    return mix(land,skyCol,sky);
}
