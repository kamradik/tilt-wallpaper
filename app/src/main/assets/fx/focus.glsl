// Focus Rack: the tilt racks the focus through the scene like a cinema focus puller. Tilting forward pulls the
// focus to the far planes, back to the near ones; whatever is off the focal plane goes soft by how far it is from
// it, with the slight colour fringing of a real lens's defocus and the faint "breathing" zoom of a refocusing lens.
// Sliders: blur (how soft the out-of-focus planes get), focus travel (how far one tilt moves the focus).

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float blur=params.x,travel=params.y;
    // The focal plane: 0 far .. 1 near, moved by the forward-back tilt.
    float focal=clamp(.55+tilt.y*(.35+.5*travel)+tilt.x*.1,0.0,1.0);
    // Breathing: the picture scales a hair as the focus moves.
    vec2 b=(uv-.5)*(1.0-(focal-.55)*.02)+.5;
    float d=textureLod(u_depth,coords(b),1.0).r;
    float off=abs(d-focal);
    float lod=smoothstep(.04,.45,off)*(1.0+5.0*blur);
    // Defocus fringe: the out-of-focus parts split a little in colour, front and back planes opposite ways.
    vec2 fr=(uv-.5)*.004*smoothstep(.1,.5,off)*sign(d-focal)*blur;
    vec3 col=vec3(photo(b+fr,lod).r,photo(b,lod).g,photo(b-fr,lod).b);
    // The sharp plane gets a touch of micro-contrast, as a lens's sweet spot does.
    vec3 sharp=photo(b,0.0);float inFocus=1.0-smoothstep(.0,.12,off);
    col=mix(col,sharp+(sharp-photo(b,1.5))*.35,inFocus);
    return col;
}
