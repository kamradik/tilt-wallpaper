// Parallax Relief: the photo becomes a relief carved to the depth map. A ray from the eye marches into the
// surface until it meets it (parallax occlusion mapping), so near parts stand out and far ones sink back as the
// phone tilts; a second, shorter march toward the light shades the slopes that face away. The height is read
// from a smoothed level of the depth map, so sharp depth steps lean instead of tearing into streaks, and a
// subject with a mask moves as one piece. Sliders: height, shadows. Switch: self-shadowing.

float pmHeight(vec2 uv){return 1.0-textureLod(u_depth,coords(uv),2.2).r;}   // 0 = nearest, 1 = deepest

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float scale=mix(.012,.045,params.x);
    vec2 ray=-tilt*scale;
    const int STEPS=28;
    float stepSize=1.0/float(STEPS);
    vec2 uvStep=ray*stepSize;
    vec2 p=uv;float layer=0.0,h=pmHeight(p);
    for(int i=0;i<STEPS;i++){
        if(layer>=h)break;
        p+=uvStep;layer+=stepSize;h=pmHeight(p);
    }
    // Secant refinement between the last two samples.
    vec2 prev=p-uvStep;float prevH=pmHeight(prev),prevLayer=layer-stepSize;
    float after=h-layer,before=prevH-prevLayer;
    vec2 hit=mix(p,prev,clamp(after/min(-1e-4,after-before),0.0,1.0));

    vec3 col=photo(hit,0.0);
    // Slope shading from the smoothed relief: surfaces facing the light brighten, those facing away darken.
    vec2 e=vec2(2.0/u_imageSize.x,2.0/u_imageSize.y)*4.0;
    vec2 grad=vec2(pmHeight(hit+vec2(e.x,0.0))-pmHeight(hit-vec2(e.x,0.0)),pmHeight(hit+vec2(0.0,e.y))-pmHeight(hit-vec2(0.0,e.y)));
    vec2 light=normalize(vec2(-.5,.8)+tilt*.7);
    float slope=clamp(dot(grad,light)*6.0*params.x,-1.0,1.0);
    col*=1.0+.22*slope*params.y;

    if(u_toggle>.5){
        // Self-shadowing: from the hit point toward the light, is the relief higher (nearer) than the ray?
        float h0=pmHeight(hit),shadow=0.0;
        for(int j=1;j<=8;j++){
            float t=float(j)/8.0;float hj=pmHeight(hit+light*scale*.6*t);
            shadow=max(shadow,clamp((h0-hj-t*.12)*6.0,0.0,1.0));
        }
        col*=1.0-.4*shadow*params.y;
    }
    // A subject with a mask stays whole: drawn as one piece, shifted a little toward the viewer.
    float m=textureLod(u_depth,coords(uv),0.0).b>.9?subjectAt(uv-tilt*scale*.2):0.0;
    col=mix(col,photo(uv-tilt*scale*.2,0.0),m);
    return col;
}
