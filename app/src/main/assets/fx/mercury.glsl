// Liquid Mercury: the photo reflected in a pool of liquid metal. A slow, heavy swell runs over the surface; its
// normals bend the reflection, the metal turns the picture silver (keeping a little of its colour, as polished
// chrome does), the slopes facing the light flash white and the troughs go dark. The brighter parts of the
// photo turn to chrome first; the Chrome slider spreads it over the rest. Sliders: ripples, chrome. Switch: the
// surface flows on its own (otherwise it moves only with the tilt).

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float ripple=params.x,chrome=params.y;
    float t=u_toggle>.5?u_time*.45:0.0;
    const vec3 Y=vec3(.2126,.7152,.0722);
    vec2 q=(uv-.5)*vec2(u_aspect,1.0);

    // The swell: a few broad, slow waves (heavy liquid), plus the tilt sloshing them.
    vec2 g=vec2(0.0);float h=0.0;
    vec2 dirs[4];dirs[0]=vec2(.8,.6);dirs[1]=vec2(-.5,.86);dirs[2]=vec2(.2,-.98);dirs[3]=vec2(-.95,-.3);
    for(int i=0;i<4;i++){
        float f=5.0+float(i)*3.2,a=.5/(1.0+float(i));
        float ph=dot(q,dirs[i])*f-t*(1.0+.3*float(i))+dot(tilt,dirs[i])*2.0;
        h+=a*sin(ph);g+=a*f*cos(ph)*dirs[i];
    }
    g*=.012*(.3+ripple);
    vec3 n=normalize(vec3(-g,1.0));

    // Reflection: the photo, displaced by the surface normal, as a mirror sees it.
    vec3 refl=photo(uv+n.xy*.035*(.4+ripple),.8);
    float l=dot(refl,Y);
    // Chrome: silver with a little of the picture's hue, contrast pushed like polished metal.
    // Chrome curve: polished metal shows the world as hard light and dark bands, not a flat grey copy, so the
    // brightness is folded into bands that slide with the surface normal.
    float bands=.5+.5*cos(l*7.5+n.x*9.0-n.y*6.0+h*2.0);
    float chromeL=mix(pow(l,1.5),bands,.55);
    vec3 metal=mix(vec3(chromeL),refl*chromeL/max(l,.05),.22);
    // Light: slopes facing the light flash, troughs darken (the metal reflects a dark room there).
    vec3 L=normalize(vec3(-.4+tilt.x*.8,.6+tilt.y*.8,1.0));
    float diff=dot(n,L);
    float spec=pow(max(dot(n,normalize(L+vec3(0.0,0.0,1.0))),0.0),90.0);
    metal*=.55+.55*diff;
    metal=metal*vec3(.96,.98,1.02)+vec3(1.0,.99,.97)*spec*.8;

    // Where it is metal: bright areas first, everything at full Chrome; a soft boundary that wobbles with the swell.
    vec3 base=photo(uv,0.0);
    float amount=smoothstep(.85-chrome*.9,1.05-chrome*.6,dot(base,Y)+h*.06);
    amount=max(amount,chrome*chrome*.6);
    return mix(base,metal,amount);
}
