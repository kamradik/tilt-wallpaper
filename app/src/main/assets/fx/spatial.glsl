// Spatial Scene: the photo as a continuous volume rather than two cut-out layers. Every point moves by its own
// depth around a pivot in the middle of the depth range, as if the screen were a window onto the scene. A ray is
// marched from the nearest depth slice to the farthest; the first slice where the map has a surface at that depth
// wins, so near things really cover far ones. Where nothing is found (the background a near object has uncovered)
// the painted background plate shows, or a soft fill without one. Focus falls off with distance from the pivot,
// and a faint light from the tilt's direction models the relief.
// Sliders: strength (how far the viewpoint moves), depth of field.

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float strength=params.x,dof=params.y;
    vec2 shift=tilt*(.025+.05*strength)*vec2(1.0,1.0);
    const int STEPS=44;
    vec2 hit=uv;float found=0.0,depth=.5;
    for(int i=0;i<STEPS;i++){
        float z=1.0-float(i)/float(STEPS-1);              // near (1) to far (0)
        vec2 src=uv-shift*(z-.5);
        float d=texture(u_depth,coords(src)).r;
        if(abs(d-z)<1.8/float(STEPS)||d>z){hit=src;depth=d;found=1.0;break;}
    }
    vec3 col;
    if(found>.5){
        // Depth of field: sharp at the pivot depth, softer toward the nearest and the farthest planes.
        float blur=dof*3.2*smoothstep(.12,.5,abs(depth-.55));
        col=photo(hit,blur);
    }else{
        vec2 back=uv+shift*.5;                           // the far plane
        col=u_hasPlate>.5?plate(back,1.0):photo(back,4.0);
    }
    // Relief light: surfaces facing the tilt's light catch a little more of it.
    vec2 e=vec2(.004,0.0);
    float gx=depthAt(hit+e.xy)-depthAt(hit-e.xy),gy=depthAt(hit+e.yx)-depthAt(hit-e.yx);
    vec2 light=normalize(vec2(-.5,.8)-tilt*.8);
    col*=1.0+clamp(dot(vec2(gx,gy),light)*6.0,-.12,.12);
    // The far planes recede into a cooler, lighter air.
    col=mix(col,col*.9+vec3(.03,.045,.07),(1.0-depth)*.35*strength);
    return col;
}
