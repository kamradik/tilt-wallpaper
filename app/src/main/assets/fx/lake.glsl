// Mirror Lake: still water fills the lower part of the frame and reflects the photo above it. The tilt moves the
// horizon a little and slides the reflection against the scene, as real water does; a breeze writes small ripples
// into the reflection and a touch drops in a ring. The reflection is clearest at the horizon and darker and
// deeper toward the viewer, with the Fresnel falloff of real water.
// Sliders: ripples, water level.

float mlNoise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    float a=hash2(i).x,b=hash2(i+vec2(1,0)).x,c=hash2(i+vec2(0,1)).x,d=hash2(i+vec2(1,1)).x;return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float ripples=params.x,level=mix(.22,.55,params.y);float t=u_time*u_motion;
    float horizon=level+tilt.y*.03;
    // Above the water: the photo, lifted so its lower part sits at the horizon.
    if(uv.y>=horizon)return photo(vec2(uv.x-tilt.x*.006,uv.y),0.0);
    // Below: depth under the horizon, in perspective (ripples crowd toward the horizon).
    float below=horizon-uv.y,persp=1.0/(below*6.0+.08);
    vec2 w=vec2(uv.x*u_aspect*persp*.6,persp*.9);
    vec2 wave=vec2(mlNoise(w*3.0+vec2(t*.4,t*.15))-.5,mlNoise(w*3.0+vec2(-t*.3,t*.2)+7.0)-.5)*(.005+.02*ripples)*(.4+below*3.5);
    // A touch: rings spreading on the water, flattened by perspective.
    if(u_touch.w>.5&&u_touch.z<4.0){
        vec2 tp=vec2(u_touch.x,1.0-u_touch.y);vec2 d=(uv-tp)*vec2(u_aspect,4.0);float dist=length(d),age=u_touch.z;
        wave+=normalize(d+1e-5)*sin(dist*55.0-age*12.0)*exp(-dist*6.0-age*1.2)*.012*vec2(1.0,.3);
    }
    // The reflection: the scene mirrored about the horizon, shifted by the tilt against the scene.
    vec2 m=vec2(uv.x+tilt.x*.012,horizon+below*1.05)+wave;
    vec3 refl=photo(m,.6+below*2.5);
    // Water body colour, from the photo's own sky, deeper toward the viewer.
    vec3 sky=photo(vec2(.5,.95),5.0);
    vec3 deep=mix(vec3(.02,.05,.08),sky*.25,.4);
    float fres=.25+.75*pow(1.0-clamp(below*1.8,0.0,1.0),3.0);
    vec3 col=mix(deep,refl*.92,fres);
    // Glints where the ripples face the light; a soft line of light at the horizon.
    col+=vec3(1.0)*pow(max(0.0,wave.x*120.0+wave.y*60.0),4.0)*.08*ripples;
    col+=sky*smoothstep(.012,0.0,below)*.25;
    return col;
}
