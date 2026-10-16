// Oil Painting: the photo repainted in thick oil. A Kuwahara filter (the four quarters around each point, the
// calmest one wins) flattens areas into brush-sized patches with crisp edges between them; strokes run along the
// image's own contours. The paint has body: its relief, taken from the painted image and a stroke texture, catches
// the light from the tilt's direction, and a canvas weave shows in the thin parts.
// Sliders: strokes (brush size), relief (how thick the paint stands).

vec3 opQuarter(vec2 uv,vec2 dirA,vec2 dirB,float lod,out float variance){
    vec3 sum=vec3(0.0),sq=vec3(0.0);
    for(int i=0;i<4;i++)for(int j=0;j<4;j++){
        vec2 o=dirA*(float(i)+.5)+dirB*(float(j)+.5);
        vec3 c=photo(uv+o,lod);sum+=c;sq+=c*c;
    }
    sum/=16.0;sq/=16.0;vec3 v=abs(sq-sum*sum);variance=v.r+v.g+v.b;return sum;
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float size=mix(.0025,.0075,params.x),relief=params.y;
    // Stroke direction: along the contour (perpendicular to the luminance gradient) of a blurred copy.
    vec2 e=vec2(.006,0.0);
    float lx=dot(photo(uv+e.xy,3.0)-photo(uv-e.xy,3.0),vec3(.333)),ly=dot(photo(uv+e.yx,3.0)-photo(uv-e.yx,3.0),vec3(.333));
    // Where the image has no contour the brush keeps one steady slant, so flat areas get calm parallel strokes.
    vec2 grad=vec2(lx,ly);float sure=smoothstep(.01,.06,length(grad));
    vec2 along=normalize(mix(vec2(.8,.6),length(grad)>1e-5?normalize(vec2(-grad.y,grad.x)):vec2(.8,.6),sure)+1e-4);
    vec2 across=vec2(-along.y,along.x);
    // Anisotropic Kuwahara: quarters stretched along the stroke.
    vec2 A=along*size*1.6,B=across*size*.8;A.x/=u_aspect;B.x/=u_aspect;  // square steps on screen
    float lod=params.x*1.2,v0,v1,v2,v3;
    vec3 c0=opQuarter(uv,A,B,lod,v0),c1=opQuarter(uv,-A,B,lod,v1),c2=opQuarter(uv,A,-B,lod,v2),c3=opQuarter(uv,-A,-B,lod,v3);
    // Generalised Kuwahara: every quarter counts, the calm ones far more, so patches have no pixel noise.
    float w0=1.0/pow(1.0+v0*400.0,4.0),w1=1.0/pow(1.0+v1*400.0,4.0),w2=1.0/pow(1.0+v2*400.0,4.0),w3=1.0/pow(1.0+v3*400.0,4.0);
    vec3 col=(c0*w0+c1*w1+c2*w2+c3*w3)/(w0+w1+w2+w3);
    // A little more colour, as pigment has.
    float l=dot(col,vec3(.299,.587,.114));col=mix(vec3(l),col,1.18);
    // Paint relief: the painted luminance plus a stroke texture running along the strokes.
    vec2 sp=vec2(dot(uv*vec2(u_aspect,1.0),along),dot(uv*vec2(u_aspect,1.0),across));
    // Bristle marks: soft ridges along the stroke, about a brush width apart, wavering a little.
    float strokes=sin(sp.y*6.283/(size*3.2)+sin(sp.x*6.283/(size*14.0))*1.2)*.5+.5;
    float h=l*.8+strokes*.12;
    float hx=dFdx(h),hy=dFdy(h);
    vec3 n=normalize(vec3(-hx*14.0*relief,-hy*14.0*relief,1.0));
    vec3 L=normalize(vec3(-.5-tilt.x*.9,.6-tilt.y*.9,.7));
    float lit=dot(n,L);
    col*=.82+.3*lit;
    col+=vec3(1.0)*pow(max(dot(reflect(-L,n),vec3(0,0,1)),0.0),24.0)*.2*relief;
    // Canvas weave in the thin, dark parts.
    vec2 cw=uv*vec2(u_aspect,1.0)*420.0;float weave=(sin(cw.x)*sin(cw.y))*.5+.5;
    col*=1.0-.05*weave*(1.0-l);
    return col;
}
