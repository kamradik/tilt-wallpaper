// Morning Mist: fog fills the scene by depth: thick in the far planes, thinning toward the viewer, so near things
// stand out of it sharp. It drifts in slow banks and lies lower and denser near the ground. Light comes from the
// bright, far part of the sky and falls through the fog as shafts that lean with the tilt; the fog takes the
// colour of that light.
// Sliders: density, light shafts.

float mfNoise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    float a=hash2(i).x,b=hash2(i+vec2(1,0)).x,c=hash2(i+vec2(0,1)).x,d=hash2(i+vec2(1,1)).x;return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);}
float mfFbm(vec2 p){float v=0.0,a=.5;for(int i=0;i<4;i++){v+=a*mfNoise(p);p=p*2.07+vec2(3.1,1.7);a*=.5;}return v;}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float density=params.x,shafts=params.y;float t=u_time*u_motion;
    vec2 fu=uv-tilt*.006;
    vec3 base=photo(fu,0.0);
    float depth=texture(u_depth,coords(fu)).r,soft=textureLod(u_depth,coords(fu),2.0).r;
    float far=1.0-mix(depth,soft,.5);
    // The light: the colour of the brightest, farthest part of the frame (usually the sky), a little warm.
    vec3 sky=photo(vec2(.5,.92),6.0);
    vec3 light=mix(vec3(.92,.94,.96),sky*1.25+vec3(.08,.06,.03),.45);
    // Fog banks: drifting noise, denser low in the frame and in the far planes.
    vec2 p=vec2(uv.x*u_aspect,uv.y);
    float banks=mfFbm(p*vec2(2.2,4.0)+vec2(t*.025,0.0)+tilt*.15)*.6+mfFbm(p*vec2(5.0,8.0)-vec2(t*.04,0.0))*.4;
    float low=1.0-smoothstep(.15,.85,uv.y);
    float fog=clamp(pow(far,1.6)*(.55+.9*banks)*(.75+.45*low)*density*1.25,0.0,.92);
    // Shafts: a radial blur of the far, bright areas toward the light source, which leans with the tilt.
    vec2 src=vec2(.62-tilt.x*.25,1.05+tilt.y*.08);
    float rays=0.0;vec2 d=(src-uv)/12.0;vec2 q=uv;
    for(int i=0;i<12;i++){q+=d;float dz=texture(u_depth,coords(q)).r;float lum=dot(photo(q,3.0),vec3(.333));rays+=smoothstep(.55,.95,lum)*(1.0-smoothstep(.1,.35,dz));}
    rays=rays/12.0*shafts*3.2*(.6+.4*banks);
    vec3 col=mix(base,light*(.82+.25*banks),fog);
    col+=light*rays*(.4+.6*far)*.55;
    // Near things keep their contrast; the whole scene softens a touch.
    return mix(col,col*1.04-.02,depth*.5);
}
