// Liquid Level: the screen is the front of a glass and clear water fills its lower part. The surface stays on
// the true horizon as the phone rolls and sloshes when it moves. Seen through the water the photo is magnified
// and shifted, as through a real glass; just under the surface a silvery band reflects the water from below
// (total internal reflection), the surface climbs the side walls in a meniscus, and caustics ripple faintly.
// Sliders: fill level, waves. Switch: bubbles.

float lqBubbles(vec2 p,float waterTop){
    // A sparse column grid of bubbles rising at their own speed; two columns per pixel.
    float sum=0.0,cols=8.0,x=p.x*cols;
    for(int k=0;k<2;k++){
        float col=floor(x-.5)+float(k);
        vec2 h=hash2(vec2(col,3.7));
        float speed=.06+.05*h.y,size=.005+.009*h.x;
        float travel=fract(h.y*5.1+u_time*speed);
        float cx=(col+.5+.35*(h.x-.5))/cols+.008*sin(u_time*(1.3+h.y)+h.x*20.0)*travel;
        float cy=-.62+travel*(waterTop+.62);
        vec2 d=vec2(p.x-cx,p.y-cy);float r=length(d),aa=fwidth(p.y)*1.2;
        float ring=smoothstep(size+aa,size,r)*smoothstep(size*.5,size*.95,r);
        float spark=smoothstep(size*.45,0.0,length(d-vec2(-.35,.35)*size));
        float fade=smoothstep(0.0,.08,travel)*smoothstep(waterTop+.005,waterTop-.03,cy);
        sum+=(ring*.5+spark)*fade;
    }
    return sum;
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float level=params.x,waves=params.y;
    float t=u_time;
    vec2 p=(uv-.5)*vec2(u_aspect,1.0);
    // Roll keeps the waterline level with the real horizon (tilt is degrees/65); pitch nudges it.
    float roll=clamp(tilt.x,-1.0,1.0)*1.1345;
    vec2 up=vec2(sin(roll),cos(roll)),along=vec2(up.y,-up.x);
    float s=dot(p,along),height=dot(p,up);
    float surface=mix(-.42,.28,level)-tilt.y*.05;
    // Sloshing: bigger the further from level the phone is held, and it never stops completely.
    float slosh=(.3+.7*clamp(length(tilt)*1.4,0.0,1.0))*waves;
    float wave=sin(s*11.0-t*1.6)*.5+sin(s*19.0+t*2.2+1.3)*.3+sin(s*5.0+t*.8)*.2;
    float dwave=cos(s*11.0-t*1.6)*5.5+cos(s*19.0+t*2.2+1.3)*5.7+cos(s*5.0+t*.8)*1.0;
    float amp=.009*slosh;
    // Meniscus: the water climbs the glass walls at both sides of the screen.
    float wall=min(u_aspect*.5-abs(p.x),.5-abs(p.y));
    float climb=.012*exp(-max(wall,0.0)/.018);
    float line=surface+wave*amp+climb;
    float h=height-line;                                   // negative under water
    float aa=max(fwidth(height),1e-4)*1.5;
    float under=1.0-smoothstep(-aa,aa,h);

    vec3 dry=photo(uv,0.0);

    // Through the water: magnified about the glass's centre (a water-filled glass is a lens) and bent by the
    // slope of the waves; slightly softer the deeper the view.
    float depth=max(0.0,-h);
    vec2 lens=p/vec2(1.07,1.03);
    vec2 bend=along*dwave*amp*.6;
    vec2 wuv=(lens+bend)/vec2(u_aspect,1.0)+.5;
    vec3 wet=photo(wuv,depth*1.6);
    // Clear water: a gentle cyan cast that grows with depth, never murky.
    vec3 absorb=exp(-(depth+.06)*vec3(.55,.16,.1)*2.4);
    wet=wet*absorb*vec3(.95,.99,1.0)+vec3(.012,.045,.06)*(1.0-absorb.r)*2.2;

    // Caustics: soft crossing interference, faint and only near the surface.
    vec2 cp=vec2(s,height)*16.0;
    float c1=sin(cp.x+sin(cp.y*.7+t*.8)*1.6+t*.6),c2=sin(cp.x*.6-cp.y*.8+sin(cp.x*.5-t*.7)*1.4-t*.5);
    float caustic=pow(max(0.0,1.0-abs(c1+c2)*.5),8.0);
    wet*=1.0+caustic*.22*exp(-depth*3.0)*(.4+.6*waves);

    if(u_toggle>.5)wet+=vec3(.92,.97,1.0)*lqBubbles(vec2(s,height),line)*.55;

    // Under the surface, a silvery band: the surface seen from below mirrors the water (total internal
    // reflection). It is wider when the phone leans back, as the view grazes the surface more.
    float band=mix(.012,.03,clamp(.5-tilt.y,0.0,1.0));
    float tir=smoothstep(band,0.0,depth)*under;
    vec3 mirror=photo(vec2(wuv.x,wuv.y+depth*1.6),3.0)*absorb*1.12;
    wet=mix(wet,mirror,tir*.35);

    vec3 col=mix(dry,wet,under);
    // The waterline: a thin bright edge where light glances off the meniscus, brightest where the waves
    // face the light, and a faint dark line just above it where the meniscus bends the light away.
    float light=.55+.45*clamp(dot(normalize(vec2(.35,1.0)+tilt*.6),normalize(vec2(-dwave*amp*3.0,1.0))),0.0,1.0);
    float edge=exp(-pow(h/(aa*.9+.0012),2.0));
    col*=1.0-.22*exp(-pow((h-.004)/.003,2.0))*(1.0-under);
    col+=vec3(.92,.97,1.0)*edge*.42*light;
    return col;
}
