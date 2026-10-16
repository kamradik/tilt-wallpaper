// Dynamic Light: a soft lamp held above the photo. Its broad pool of light follows the tilt, brightening and
// tinting where it falls while the rest of the picture settles a little darker, and the photo's own relief
// (from its brightness) catches gentle shading and glints. A highlight shoulder keeps bright photos from
// burning out. Sliders: brightness, warm <-> cold light. Switch: sharpen.

// Highlight shoulder on brightness: linear up to KNEE, then rolling off toward 1. Scaling all three channels
// by the same factor keeps the hue, so a lit face stays a face colour instead of fading to grey.
vec3 lgShoulder(vec3 c){
    const float KNEE=.75;float l=max(max(c.r,c.g),c.b);if(l<=KNEE)return c;
    float over=l-KNEE,m=KNEE+over/(1.0+over*3.0);return c*(m/l);
}

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float power=params.x,temp=params.y;
    vec3 base=photo(uv,0.0);
    vec3 warm=vec3(1.0,.84,.62),white=vec3(1.0),cold=vec3(.76,.87,1.0);
    vec3 tint=temp<.5?mix(warm,white,temp*2.0):mix(white,cold,(temp-.5)*2.0);

    // The lamp: above the middle of the picture, swinging with the tilt.
    vec2 q=(uv-.5)*vec2(u_aspect,1.0);
    vec2 lamp=vec2(tilt.x*.42,.12+tilt.y*.42);
    vec2 to=lamp-q;float d=length(to);
    float pool=exp(-d*d*3.4);

    // Relief from the photo's brightness, slightly smoothed so it follows forms rather than noise.
    vec2 texel=1.5/u_imageSize;
    const vec3 Y=vec3(.2126,.7152,.0722);
    float l=dot(photo(uv-vec2(texel.x,0.0),1.0),Y)-dot(photo(uv+vec2(texel.x,0.0),1.0),Y);
    float r=dot(photo(uv-vec2(0.0,texel.y),1.0),Y)-dot(photo(uv+vec2(0.0,texel.y),1.0),Y);
    vec3 N=normalize(vec3(l*4.0,r*4.0,1.0));
    vec3 L=normalize(vec3(to,.6));
    float shade=mix(1.0,clamp(.55+.6*dot(N,L),0.0,1.25),.45*power);
    vec3 H=normalize(L+vec3(0.0,0.0,1.0));
    float glint=pow(max(dot(N,H),0.0),56.0)*dot(base,Y);

    // Relight: the pool gains up to +40 % (less on what is already bright) and takes the lamp's colour; outside
    // it the picture dims by up to 18 %.
    float bright=dot(base,Y);
    float gain=mix(1.0-.18*power,1.0+.4*power*(1.0-.6*bright),pool);
    vec3 lit=base*gain*mix(vec3(1.0),tint,pool*min(1.0,power*1.3))*shade;
    lit+=tint*glint*.35*power*(.3+.7*pool);
    lit=lgShoulder(lit);
    if(u_toggle>.5){
        vec3 soft=photo(uv,1.2);
        lit=clamp(lit+(base-soft)*(.25+.3*power),0.0,1.0);
    }
    return lit;
}
