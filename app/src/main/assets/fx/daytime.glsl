// Time of Day: grades the photo by the local clock (u_day: 0 midnight, .5 noon). Cool lavender dawn, clean noon,
// warm golden evening and a deep blue night with faint stars in the darkest areas; a soft sun (or moon) glow crosses
// the sky with the hours and leans with the tilt. Static between tilts: u_day changes once a second.

float dtLuma(vec3 c){return dot(c,vec3(.2126,.7152,.0722));}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float strength=params.x,night=params.y;
    float day=fract(u_day);
    float elev=-cos(day*6.28318);            // sun height: -1 midnight, 1 noon, 0 at 6:00 and 18:00
    float morning=step(day,.5);
    float dayness=smoothstep(-.18,.3,elev);
    float horizon=exp(-pow((elev-.08)/.26,2.0));// golden band around sunrise and sunset
    float dark=smoothstep(.02,-.4,elev);

    vec3 base=photo(uv-tilt*.004,0.0);
    float luma=dtLuma(base);
    // Noon: clean, a touch of contrast.
    vec3 noon=clamp((base-.5)*1.05+.5,0.0,1.0)*1.02;
    // Evening: warm highlights, amber midtones, shadows kept rich.
    vec3 gold=pow(max(base,0.0),vec3(.92,.99,1.14))*vec3(1.16,1.0,.76);
    gold+=vec3(.06,.025,0.0)*smoothstep(.3,.9,luma);
    // Dawn: cool air with lavender-lifted shadows and a peach top end.
    vec3 dawn=base*vec3(.96,.96,1.07)+vec3(.05,.03,.08)*(1.0-luma)+vec3(.04,.015,-.02)*smoothstep(.5,1.0,luma);
    // Night: moonlit blue, desaturated, darker the higher Night darkness is.
    vec3 moon=mix(vec3(luma),base,.4)*vec3(.5,.66,1.0);
    vec3 nightCol=moon*mix(.82,.28,night)+vec3(.008,.014,.035);

    vec3 graded=mix(noon,mix(gold,dawn,morning),horizon);
    graded=mix(graded,nightCol,dark);
    vec3 col=mix(base,graded,strength);

    // The light source: the sun travels left to right through the day; at night a smaller cool moon does.
    vec2 p=(uv-.5)*vec2(u_aspect,1.0);
    float sunX=clamp((day-.25)*2.0,-.1,1.1),moonX=fract(day+.5);
    moonX=clamp((moonX-.25)*2.0,-.1,1.1);
    vec2 sunPos=vec2((sunX-.5)*u_aspect*1.3,.42+.18*max(elev,0.0))+tilt*vec2(.18,.12);
    vec2 moonPos=vec2((moonX-.5)*u_aspect*1.3,.36+.12*max(-elev,0.0))+tilt*vec2(.12,.08);
    float sun=exp(-length(p-sunPos)*mix(4.5,3.0,horizon));
    vec3 sunTint=mix(vec3(1.0,.97,.9),morning>.5?vec3(1.0,.72,.62):vec3(1.0,.62,.28),horizon);
    col+=sunTint*sun*.22*dayness*strength*(1.0-.5*luma);
    float moonGlow=exp(-length(p-moonPos)*7.0);
    col+=vec3(.55,.68,1.0)*moonGlow*.12*dark*strength;

    // Stars only where the night scene is dark: sky, shadows. Two sizes, fixed but parallaxing with tilt.
    if(u_toggle>.5&&dark>.01){
        float shown=dark*strength*smoothstep(.32,.08,luma);
        vec3 stars=vec3(0.0);
        for(int l=0;l<2;l++){
            float scale=l==0?90.0:46.0;
            vec2 sp=(p+tilt*(l==0?.006:.012))*scale;
            vec2 cell=floor(sp),h=hash2(cell+float(l)*17.0);
            float a=fwidth(sp.x);
            if(h.x<(l==0?.18:.06)){
                vec2 centre=cell+.2+.6*hash2(cell*1.7+3.0);
                float d=length(sp-centre);
                float size=l==0?.06:.1;
                float star=smoothstep(size+a,size*.3,d)+exp(-d*d*18.0)*.25;
                stars+=mix(vec3(.75,.85,1.0),vec3(1.0,.92,.8),h.y)*star*(.45+.55*fract(h.y*13.7));
            }
        }
        col+=stars*shown;
    }
    return col;
}
