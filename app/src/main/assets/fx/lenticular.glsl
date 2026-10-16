// Lenticular 3D: a lenticular postcard. Under a sheet of vertical cylinder lenses the photo is printed as several
// interleaved views of the scene from slightly different angles (each shifted by its depth); as you tilt, each
// lens shows the next view, so the scene seems to turn in depth with the faint banding and the moving sheen of a
// real lens sheet. The switch adds the glints running down the lens ridges.
// Sliders: depth (how far apart the views are), lens pitch. Switch: lens glints.

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float depthAmt=params.x,pitch=mix(70.0,28.0,params.y);
    float x=uv.x*pitch*u_aspect,f=fract(x)-.5;
    // Which view this point of the lens shows: set by the tilt and by the position under the lens.
    float views=7.0;
    float v=clamp(floor((tilt.x*.5+.5+f*.35)*views),0.0,views-1.0)/(views-1.0)*2.0-1.0;
    // A view from angle v: everything shifted by its depth around the middle of the range.
    float d=textureLod(u_depth,coords(uv),1.0).r;
    vec2 shift=vec2(v*.07*depthAmt*(d-.45),tilt.y*.02*(d-.45));
    vec3 col=photo(uv+shift,0.0);
    // The lens sheet: slight banding across each lens and a soft sheen that slides with the tilt.
    float z=sqrt(max(0.0,.25-f*f))*2.0;
    col*=.86+.14*z;
    float sheen=pow(max(0.0,1.0-abs(f-(tilt.x*.4))*3.0),4.0);
    col+=vec3(.9,.95,1.0)*sheen*.06;
    if(u_toggle>.5){float glint=pow(max(0.0,1.0-abs(f+.18-tilt.x*.3)*14.0),3.0)*(.5+.5*sin(uv.y*12.0+tilt.y*6.0));col+=vec3(1.0)*glint*.18;}
    return col;
}
