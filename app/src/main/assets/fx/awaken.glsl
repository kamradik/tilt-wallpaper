// Awakening: each time the wallpaper becomes visible (u_wake.x restarts at 0 on unlock), the photo surfaces from a
// deep, bright, slightly zoomed blur and settles into the tilting glass. The renderer keeps frames coming only for the
// first 1.2 s after waking, so the longest Duration ends at 1.15 s; with Reduce Motion it simply shows the photo.

vec3 effect(vec2 uv,vec2 tilt,vec2 params){
    float blur=params.x;
    float duration=mix(.45,1.15,params.y);
    float t=u_motion<.5?1.0:clamp(u_wake.x/duration,0.0,1.0);
    // Ease out: most of the reveal happens early, then it glides in.
    float r=pow(1.0-t,2.6);
    vec3 settled=classic(uv);
    if(r<.002)return settled;

    float zoom=1.0+r*mix(.03,.09,blur);
    vec2 zuv=.5+(uv-.5)/zoom+tilt*.01*r;
    // Blur measured in screen pixels, so it looks the same for any photo resolution.
    float pxPerScreen=fwidth(uv.y)*u_imageSize.y*u_coverScale.y/u_zoom;
    float lod=log2(max(1.0,pxPerScreen))+r*mix(3.0,6.5,blur);
    vec2 o=exp2(lod)*.5/u_imageSize*u_zoom/u_coverScale*r;
    vec3 soft=(photo(zuv+vec2(o.x,o.y*.4),lod)+photo(zuv+vec2(-o.x*.4,o.y),lod)
              +photo(zuv-vec2(o.x,o.y*.4),lod)+photo(zuv-vec2(-o.x*.4,o.y),lod))*.25;
    // Waking light: lifted exposure and a little less colour that both come back as it sharpens.
    float luma=dot(soft,vec3(.2126,.7152,.0722));
    soft=mix(soft,vec3(luma),.25*r);
    soft=soft*(1.0+.12*r)+vec3(.05,.05,.06)*r*r;
    // Hand over to the tilting glass once the blur is nearly gone, where the two are almost identical.
    return mix(settled,soft,smoothstep(0.0,.25,r));
}
