// Depth Parallax: the photo comes apart into two layers, as on the iOS lock screen. The main subject (its mask
// comes with the depth map, see common.glsl subjectAt) moves as one rigid cut-out; the background slides the
// other way, its far parts more than its near ones. Where the subject used to stand, the background that now
// shows is filled from the nearest background beyond the subject's edge, softened, instead of repeating the
// subject. The subject casts a soft contact shadow and catches a faint rim of light on the side facing it.
// Sliders: strength, depth (how far the layers separate).

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float strength=params.x,deep=params.y;
    vec2 move=tilt*(.012+.035*deep)*(.35+strength);
    vec2 dF=-move*.35,dB=move;                   // subject toward the viewer's side, background away
    vec2 rel=dF-dB;float relLen=length(rel);vec2 relDir=rel/max(relLen,1e-5);

    // Subject layer: a rigid cut-out sampled where it came from.
    vec2 uf=uv-dF;
    float m=subjectAt(uf);
    vec3 subject=photo(uf,0.0);

    // Background layer: far parts move the full amount, near ones less.
    vec2 ub=uv-dB*(1.0-.45*depthAt(uv-dB));
    vec3 back=photo(ub,0.0);
    float covered=subjectAt(ub);
    if(covered>.05&&relLen>1e-4){
        // The sample landed on the subject: walk away from the subject's motion until the background shows.
        vec2 p=ub;float found=0.0;
        for(int i=1;i<=14;i++){
            p=ub-relDir*relLen*.16*float(i);
            if(subjectAt(p)<.15){found=1.0;break;}
        }
        // A little further and slightly blurred, so the fill has no hard copy of the edge.
        vec3 fill=photo(p-relDir*.006,2.2);
        if(found<.5)fill=photo(ub,5.0);
        back=mix(back,fill,smoothstep(.15,.6,covered));
    }

    // Contact shadow: the cut-out shades the background just beside it, away from the light (up-left).
    vec2 light=normalize(vec2(-.5,.85)-tilt*.6);
    float shade=subjectAt(uv-dF+light*.012*(.5+deep))*(1.0-m);
    back*=1.0-.32*shade*(.4+.6*strength);

    // Rim light: the subject's edge facing the light brightens a touch, which reads as a separate layer.
    vec2 e=vec2(.004);
    vec2 grad=vec2(subjectAt(uf+vec2(e.x,0.0))-subjectAt(uf-vec2(e.x,0.0)),subjectAt(uf+vec2(0.0,e.y))-subjectAt(uf-vec2(0.0,e.y)));
    float rim=clamp(dot(-grad,light),0.0,1.0)*m;
    subject+=vec3(1.0,.98,.94)*rim*.12;

    return mix(back,subject,m);
}
