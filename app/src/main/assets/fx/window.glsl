// Window: the photo is the view outside, seen through a painted wooden window that fills the screen.
// Three planes stand one behind the other: the frame's front face, the glass a frame-thickness behind it,
// and the view far beyond. Tilting moves the eye, so the frame shows its inner sides and the view slides
// behind the glass (parallax); the glass catches a soft reflection that moves the other way.
// Sliders: depth of the view, frame width. Switch: muntins (the cross bars dividing the panes).

float wnSmin(float a,float b,float k){float h=clamp(.5+.5*(b-a)/k,0.0,1.0);return mix(b,a,h)-k*h*(1.0-h);}

// Signed distance from q (screen units, origin at the centre, y up) to the nearest wood edge: positive in a
// pane, negative on the frame. fw is the frame width, bw the bar width; the bottom rail is a little deeper.
float wnGlass(vec2 q,float fw,float bw,bool muntins){
    vec2 hs=vec2(u_aspect*.5,.5);
    float bottom=fw*1.45;
    float e=min(min(hs.x-fw-abs(q.x),hs.y-fw-q.y),q.y+hs.y-bottom);
    if(!muntins)return e;
    float k=.006;
    float v=abs(q.x)-bw*.5;
    // Three rows of panes between the rails.
    float top=hs.y-fw,low=-hs.y+bottom,third=(top-low)/3.0;
    float h=min(abs(q.y-(low+third)),abs(q.y-(low+2.0*third)))-bw*.5;
    return wnSmin(wnSmin(e,v,k),h,k);
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float depth=params.x;
    bool muntins=u_toggle>.5;
    vec2 q=(uv-.5)*vec2(u_aspect,1.0);
    float aa=max(fwidth(q.y),1e-4)*1.2;
    float fw=mix(.03,.075,params.y)*min(1.0,u_aspect*2.1);
    float bw=fw*.6;
    // The eye's offset: the frame is a slab THICK in front of the glass; the view lies FAR behind it.
    vec2 eye=tilt*vec2(1.0,1.0);
    const float THICK=.045;
    float far=mix(.03,.11,depth);

    float front=wnGlass(q,fw,bw,muntins);
    float back=wnGlass(q-eye*THICK,fw,bw,muntins);

    // Light: daylight comes in through the window; the room behind the viewer is dimmer and warm.
    vec3 outside=photo(vec2(.5,.62),9.0);                       // the view's overall colour
    float outLum=dot(outside,vec3(.2126,.7152,.0722));
    vec3 daylight=mix(vec3(1.0,.98,.95),outside/max(outLum,.05),.25);
    vec3 paint=vec3(.93,.91,.87);
    // Brush texture under the paint: a faint, fine noise (no regular pattern, so it never shimmers).
    float grain=hash2(floor(q*vec2(700.0,90.0))).x;

    // ---- the view, far behind the glass, slightly enlarged so parallax never reaches its edge
    vec2 viewP=q-eye*far;
    vec2 vuv=viewP/vec2(u_aspect,1.0)/1.12+.5;
    vec3 view=photo(vuv,0.0);
    // Glass: a faint cool cast, a reflection band sliding against the parallax, and the room's dim reflection.
    vec3 glass=view*vec3(.97,.99,1.0);
    float diag=dot(q+eye*.25,normalize(vec2(.55,1.0)));
    float band=smoothstep(.10,.0,abs(diag-.18))*.06+smoothstep(.05,.0,abs(diag+.02))*.035;
    glass+=vec3(1.0)*band*(.6+.4*outLum);
    vec3 room=photo(vec2(1.0-vuv.x,vuv.y),6.0)*.1;
    glass=mix(glass,glass+room,.35);
    // Putty line and a soft shadow just inside each pane, where the frame meets the glass.
    float inPane=max(back,0.0);
    glass*=mix(.80,1.0,smoothstep(0.0,.018,inPane));
    glass*=1.0-.35*(1.0-smoothstep(0.0,aa*2.5,inPane));

    // ---- the inner sides of the frame, seen between the front face and the glass when tilted
    // Their normal points into the pane they face: back - front changes sign across them.
    vec2 g=vec2(dFdx(back),dFdy(back));
    vec2 n=normalize(g+1e-6);
    float lit=clamp(dot(n,normalize(vec2(.3,-1.0))),0.0,1.0);   // daylight falls in from above, outside
    vec3 side=paint*mix(.55,.86,lit)*daylight;
    side*=.985+.03*grain;

    // ---- the front face of the frame
    float wood=-front;                       // distance into the wood from its inner edge
    // A rounded bevel along the inner edge catches the daylight; the flat face gets the room's light,
    // brighter at the top of the window, with soft occlusion in the corners.
    float bevel=1.0-smoothstep(0.0,.009,wood);
    float face=mix(.84,.93,uv.y);
    vec3 frame=paint*face*(.985+.03*grain);
    frame=mix(frame,paint*daylight*1.04,bevel*.85);
    frame*=1.0-.12*smoothstep(.0,.04,wood)*(1.0-smoothstep(.04,.12,wood));
    // The outer edge of the screen reads as the wall's reveal: a shade deeper toward the borders.
    vec2 hs=vec2(u_aspect*.5,.5);
    float border=min(hs.x-abs(q.x),hs.y-abs(q.y));
    frame*=mix(.86,1.0,smoothstep(0.0,fw*.6,border));
    {
        // A crisp highlight on the bevel crest, moving with the tilt.
        float crest=exp(-pow((wood-.004)/.0022,2.0));
        frame+=vec3(1.0,.99,.96)*crest*.10*(.5+.5*clamp(dot(n,normalize(-tilt+vec2(.2,.8))),0.0,1.0));
    }

    // Compose: front face where the wood is, then the inner side where the ray hits the frame's back edge,
    // else the glass.
    float isFront=smoothstep(aa,-aa,front);
    float isSide=smoothstep(aa,-aa,back)*(1.0-isFront);
    vec3 col=glass;
    col=mix(col,side,isSide);
    col=mix(col,frame,isFront);
    return col;
}
