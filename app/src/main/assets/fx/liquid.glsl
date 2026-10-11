// Liquid Level: clear water fills the bottom of the screen and its surface stays level with the
// real horizon as the phone rolls, sloshing gently when it is moved off level.

float liquidBubbles(vec2 p,float waterTop){
    // A sparse column grid of bubbles rising at their own speed; one cell per pixel (plus its
    // neighbour) keeps it to two evaluations.
    float sum=0.0;
    float cols=7.0;
    float x=p.x*cols;
    for(int k=0;k<2;k++){
        float col=floor(x-.5)+float(k);
        vec2 h=hash2(vec2(col,3.7));
        float speed=.07+.06*h.y;
        float size=(.006+.010*h.x);
        float cx=(col+.5+.35*(h.x-.5))/cols+.006*sin(u_time*(1.3+h.y)+h.x*20.0);
        float travel=fract(h.y*5.1+u_time*speed);
        float cy=-.6+travel*(waterTop+.6);
        vec2 d=vec2(p.x-cx,p.y-cy);
        float r=length(d);
        float aa=fwidth(p.y)*1.2;
        float ring=smoothstep(size+aa,size,r)*smoothstep(size*.45,size*.95,r);
        float spark=smoothstep(size*.5,0.0,length(d-vec2(-.35,.35)*size));
        float fade=smoothstep(0.0,.08,travel)*smoothstep(waterTop+.01,waterTop-.04,cy);
        sum+=(ring*.55+spark*.9)*fade;
    }
    return sum;
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float level=params.x,waves=params.y;
    // Square screen space centred on the middle, y up.
    vec2 p=(uv-.5)*vec2(u_aspect,1.0);
    // Roll keeps the waterline on the true horizon (tilt is degrees/65); pitch nudges the level.
    float roll=clamp(tilt.x,-1.0,1.0)*1.1345;
    vec2 up=vec2(sin(roll),cos(roll));
    vec2 along=vec2(up.y,-up.x);
    float s=dot(p,along);
    float height=dot(p,up);
    // Water fills by area: level 0..1 maps to a line between -0.42 and +0.25 of the screen height.
    float surface=mix(-.42,.25,level)-tilt.y*.06;
    // Waves grow with how far from level the phone is held, and never quite stop.
    float slosh=(.25+.75*clamp(length(tilt)*1.6,0.0,1.0))*waves;
    float t=u_time;
    float wave=sin(s*14.0-t*1.7)*.55+sin(s*23.0+t*2.3+1.3)*.3+sin(s*6.0+t*.9)*.15;
    float dwave=cos(s*14.0-t*1.7)*.55*14.0+cos(s*23.0+t*2.3+1.3)*.3*23.0+cos(s*6.0+t*.9)*.15*6.0;
    float amp=.006*slosh;
    float line=surface+wave*amp;
    float h=height-line;// negative under water
    float aa=max(fwidth(height),1e-4)*1.5;
    float under=1.0-smoothstep(-aa,aa,h);

    vec3 dry=photo(uv,0.0);

    // Refraction: the image under water is magnified toward the surface and bent by the wave slope.
    float depth=max(0.0,-h);
    vec2 bend=along*(dwave*amp*.9)+up*(-depth*.05);
    vec2 wuv=uv+bend/vec2(u_aspect,1.0)*.6;
    vec3 wet=photo(wuv,depth*4.0);
    // Absorption: red goes first, so deeper water turns teal and darker.
    vec3 absorb=exp(-(depth+.06)*vec3(2.6,1.0,.7)*1.6);
    wet*=absorb;
    // In-scattering: the water itself glows faintly teal, more so the deeper it is.
    wet+=vec3(.03,.10,.13)*(1.0-absorb.g)*1.4;

    // Caustics: two cheap crossing interference patterns focused into bright threads.
    vec2 cp=vec2(s,height)*22.0;
    float c1=sin(cp.x+sin(cp.y*.7+t*.8)*1.6+t*.6);
    float c2=sin(cp.x*.6-cp.y*.8+sin(cp.x*.5-t*.7)*1.4-t*.5);
    float caustic=pow(max(0.0,1.0-abs(c1+c2)*.5),6.0);
    wet+=vec3(.75,.95,1.0)*caustic*.35*exp(-depth*2.5)*(.4+.6*waves);

    // Bubbles rise only with the switch on.
    if(u_toggle>.5){
        float b=liquidBubbles(vec2(s,height),line);
        wet+=vec3(.9,.97,1.0)*b*.6;
    }

    // Surface band: Fresnel sheen just under the line, a bright meniscus, and a soft shadow line.
    float below=max(0.0,-h);
    float sheen=exp(-below*40.0)*.35;
    float meniscus=exp(-pow(h/(aa*1.6+.0025),2.0));
    float tiltLight=.6+.4*clamp(dot(normalize(vec2(.4,1.0)+tilt*.8),vec2(-dwave*amp*4.0,1.0)),0.0,1.0);
    wet=mix(wet,vec3(.86,.94,1.0),sheen*tiltLight);
    vec3 col=mix(dry,wet,under);
    // Thin contact shadow above the waterline from the meniscus curve.
    col*=1.0-.18*exp(-max(0.0,h)*90.0)*(1.0-under);
    col+=vec3(.95,.98,1.0)*meniscus*.55*tiltLight;
    return col;
}
