// Glass Mosaic: the photo is set in square tiles of thick glass. Each tile turns on its own with the tilt, and
// the turn travels across the wall as a slow wave, so the tiles never move as one sheet. A turned tile shows its
// piece of the photo foreshortened and shifted as through a lens; its bevelled rim bends the image and catches the
// light on the side facing it, and the grout between tiles stays dark.
// Sliders: tiles (how many), turn (how far the tiles rotate).

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float count=mix(7.0,20.0,params.x),turn=params.y;float t=u_time*u_motion;
    vec2 p=vec2(uv.x*u_aspect,uv.y)*count;
    vec2 cell=floor(p),f=fract(p)-.5;
    // The wave: each tile follows the tilt with a lag that depends on where it is.
    vec2 centre=(cell+.5)/count;float lag=sin(t*1.1-length(centre-vec2(.5*u_aspect,.5))*9.0)*.35;
    vec2 a=(tilt*(1.0+lag)+vec2(sin(t*.4+cell.y*.7),cos(t*.33+cell.x*.6))*.1)*turn*1.7;
    // Foreshortening of a tile turned by angle a (x around the vertical axis, y around the horizontal).
    vec2 local=f/vec2(cos(a.x),cos(a.y));
    float gap=.045;
    float body=step(abs(local.x),.5-gap)*step(abs(local.y),.5-gap);
    // The tile is a lens: its piece of the photo shifts with the turn and is magnified a little.
    vec2 tileUv=(cell+.5+local*.9+a*.35)/count;tileUv.x/=u_aspect;
    // Bevel: the last part of the tile toward its rim bends the image outward and lights up.
    vec2 r=abs(local)/(.5-gap);float rim=smoothstep(.72,1.0,max(r.x,r.y));
    vec2 nrm=sign(local)*vec2(step(r.y,r.x),step(r.x,r.y));
    tileUv+=nrm*rim*.012/count*8.0/vec2(u_aspect,1.0);
    vec3 col=vec3(photo(tileUv+nrm*rim*.002,0.0).r,photo(tileUv,0.0).g,photo(tileUv-nrm*rim*.002,0.0).b);
    // Light: the tile's face brightens as it turns toward the light, its rim more so on the lit side.
    vec2 light=normalize(vec2(-.5,.8));
    // The turned face: the edge that comes toward the viewer is lit, the one that goes away darkens.
    float face=dot(a,light)*.5;
    col*=1.0+clamp(face,-.25,.25)+clamp(dot(local,a)*1.1,-.3,.3);
    col+=vec3(1.0)*rim*max(dot(nrm,light),0.0)*.35+vec3(.6,.75,1.0)*rim*.06;
    // A glint that sweeps across tiles facing the light.
    float glint=pow(max(0.0,1.0-abs(dot(local,vec2(.7,.7))-a.x*1.6+a.y*1.2)*2.2),8.0);
    col+=vec3(1.0)*glint*.12*turn;
    // Grout, darker on the side each tile's shadow falls.
    vec3 grout=photo(uv,4.0)*(.1+.08*step(0.0,dot(f,-light)));
    return mix(grout,col,body);
}
