// Portrait: the bokeh of a fast lens, from the depth map and the subject mask. The subject stays sharp; the
// background blurs with a true disc (a golden-angle spiral of samples), more the farther it is behind the focus
// plane, and its bright points open into round bokeh balls that turn cat's-eye toward the frame's edges, as
// with real lenses. Samples landing on the subject are left out of the background's blur, so the subject never
// bleeds a halo onto it. Sliders: aperture (blur), focus (how far behind the subject the sharp zone reaches).
// Switch: the tilt racks the focus gently in and out.

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float aperture=params.x;
    const vec3 Y=vec3(.2126,.7152,.0722);
    vec4 d0=textureLod(u_depth,coords(uv),0.0);
    bool hasMask=d0.b>.9;
    float subject=subjectAt(uv);
    // Focus depth: the subject's (it stands near), pushed back by the Focus slider, racked by the tilt.
    float focus=mix(.85,.45,params.y)+(u_toggle>.5?tilt.y*.18:0.0);
    float depth=d0.r;
    // Circle of confusion: behind the focus plane it grows with distance; in front of it, a little.
    float coc=max(0.0,focus-depth)*1.6+max(0.0,depth-focus-.1)*.5;
    coc=clamp(coc,0.0,1.0)*(1.0-subject);
    float maxR=mix(.006,.03,aperture);
    float r=coc*maxR;
    vec3 sharp=photo(uv,0.0);
    if(r<.0008)return sharp;

    // Cat's eye: toward the corners the aperture is clipped by the lens barrel, so the disc narrows radially.
    vec2 q=(uv-.5)*vec2(u_aspect,1.0);
    float off=length(q);vec2 radial=off>1e-4?q/off:vec2(0.0);
    float squeeze=1.0-.45*smoothstep(.2,.65,off);

    vec3 sum=vec3(0.0);float total=0.0;
    const int N=28;
    for(int i=0;i<N;i++){
        float k=(float(i)+.5)/float(N);
        float a=float(i)*2.39996;
        vec2 o=vec2(cos(a),sin(a))*sqrt(k);
        // Narrow the disc along the radial direction toward the frame edges.
        o-=radial*dot(o,radial)*(1.0-squeeze);
        vec2 p=uv+o*r/vec2(u_aspect,1.0);
        vec3 c=photo(p,1.0);
        // Leave out the subject (no halo) and sharper background nearer than this pixel (no bleeding of near onto far).
        float m=hasMask?subjectAt(p):0.0;
        float w=(1.0-m)*(1.0+pow(dot(c,Y),4.0)*10.0);  // bright points weigh more: they open into balls
        sum+=c*w;total+=w;
    }
    vec3 blur=total>0.0?sum/total:sharp;
    return mix(sharp,blur,smoothstep(0.0,.25,coc));
}
