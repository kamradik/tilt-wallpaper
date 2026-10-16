// Spectral Prism: the photo seen through a glass prism. Light of each colour bends by a different amount, so
// the picture splits into a smooth spectrum that grows toward the edges of the frame and with the tilt, and a
// band of refracted sunlight fans out into a rainbow across it, sweeping as the phone turns.
// Sliders: dispersion, edge threshold (how much of the centre stays clean). Switch: facet glints, bright
// sparks where strong edges face the light.

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float power=params.x;
    vec2 q=(uv-.5)*vec2(u_aspect,1.0);
    // Dispersion: radial (stronger away from a clean centre) plus a sideways part from the tilt.
    float clean=mix(.05,.32,params.y);
    float radial=smoothstep(clean,clean+.45,length(q));
    vec2 split=(normalize(q+1e-5)*radial*.012+tilt*.010)*power;
    // Five spectral samples (red, yellow, green, cyan, blue), each weighted into RGB, for a smooth fringe.
    vec3 col=vec3(0.0),sum=vec3(0.0);
    for(int i=0;i<5;i++){
        float k=float(i)/4.0;
        vec3 w=clamp(vec3(1.0-k*2.0,1.0-abs(k-.5)*2.0,k*2.0-1.0),0.0,1.0)+vec3(.15);
        col+=photo(uv+split*(k-.5)*2.0,0.0)*w;sum+=w;
    }
    col/=sum;

    // A fan of refracted light: a rainbow band at an angle, sweeping with the tilt.
    vec2 dir=normalize(vec2(1.0,-.45));
    float along=dot(q,vec2(-dir.y,dir.x))-tilt.x*.45+tilt.y*.25;
    float band=exp(-along*along*28.0);
    float hue=clamp(.5+along*4.0,0.0,1.0);
    vec3 spectrum=clamp(vec3(1.5-abs(hue-.15)*4.0,1.5-abs(hue-.5)*4.0,1.5-abs(hue-.85)*4.0),0.0,1.0);
    col=1.0-(1.0-col)*(1.0-spectrum*band*.38*power);

    if(u_toggle>.5){
        // Facet glints: strong edges whose direction faces the tilt light catch a small white spark.
        vec2 t=1.5/u_imageSize;const vec3 Y=vec3(.2126,.7152,.0722);
        vec2 g=vec2(dot(photo(uv+vec2(t.x,0.0),0.0)-photo(uv-vec2(t.x,0.0),0.0),Y),dot(photo(uv+vec2(0.0,t.y),0.0)-photo(uv-vec2(0.0,t.y),0.0),Y));
        float e=length(g);vec2 light=normalize(tilt+vec2(.3,.6));
        col+=vec3(1.0,.98,.94)*pow(max(0.0,dot(g/max(e,1e-4),light)),12.0)*smoothstep(.12,.4,e)*.45*power;
    }
    return col;
}
