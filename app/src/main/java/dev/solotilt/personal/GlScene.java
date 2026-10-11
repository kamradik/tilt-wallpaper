package dev.solotilt.personal;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import android.util.DisplayMetrics;
import java.nio.*;
import java.io.*;
import java.util.*;
import static android.opengl.GLES30.*;

/**
 * GL resources for one surface: a lazily compiled program per effect (fx/common + fx/<id> + fx/main),
 * two photo layers (home and lock, so an unlock switches instantly), a quality framebuffer and the wipe mask.
 * A video layer is the same 2D texture, refilled from the decoder's OES texture on each new frame (blitVideo).
 */
final class GlScene {
    static final String[] UNIFORMS={"u_image","u_depth","u_wipeMask","u_imageSize","u_coverScale","u_crop","u_zoom","u_aspect","u_projectionX","u_projectionY","u_projectionZ","u_gap","u_blurGap","u_screenWidthMm","u_tilt","u_touch","u_params","u_toggle","u_motion","u_time","u_wake","u_alpha"};
    static final int IMAGE=0,DEPTH=1,WIPE=2,IMAGE_SIZE=3,COVER=4,CROP=5,ZOOM=6,ASPECT=7,PROJECTION=8,SCREEN_MM=13,TILT=14,TOUCH=15,PARAMS=16,TOGGLE=17,MOTION=18,TIME=19,WAKE=20,ALPHA=21;
    static final int WIPE_W=64,WIPE_H=128;

    /** One photo with its blur pyramid and optional depth map. */
    static final class Layer { int image,depth,width,height,levels,nextLevel;boolean hasDepth; }
    /** Everything one draw call needs; RenderLoop reuses two instances (current and outgoing). */
    static final class Pass {
        int layer;Effects.Effect effect=Effects.ALL[0];float a,b,toggle,cropX=.5f,cropY=.5f,scale=1.08f,alpha=1;
        void set(int layer,WallpaperConfig.Profile p){this.layer=layer;effect=p.effect();float[] v=p.current();a=v[0];b=v[1];toggle=v[2];cropX=p.cropX;cropY=p.cropY;scale=p.scale;}
        void copy(Pass o){layer=o.layer;effect=o.effect;a=o.a;b=o.b;toggle=o.toggle;cropX=o.cropX;cropY=o.cropY;scale=o.scale;}
    }

    final Context context;final String vertex,common,main;final int blur;final float pixelsPerMm;
    final Map<String,int[]> programs=new HashMap<>();
    final int[] vao=new int[1],vbo=new int[1],fbo=new int[3],scratch=new int[3],oes=new int[2];
    final Layer[] layers={new Layer(),new Layer()};
    final float[] projection=new float[15];
    int blurSource,blurLevel,blurStep,renderWidth,renderHeight,neutralDepth,video,videoSource,videoTransform;

    GlScene(Context context)throws IOException{
        this.context=context;
        DisplayMetrics dm=context.getResources().getDisplayMetrics();float dpi=(float)Math.sqrt(dm.xdpi*dm.ydpi);
        if(!Float.isFinite(dpi)||dpi<100||dpi>1000)dpi=160*dm.density;
        pixelsPerMm=dpi/25.4f;
        vertex=asset(context,"quad.glsl");common=asset(context,"fx/common.glsl");main=asset(context,"fx/main.glsl");
        blur=program(vertex,asset(context,"blur.glsl"));
        blurSource=glGetUniformLocation(blur,"u_source");blurLevel=glGetUniformLocation(blur,"u_level");blurStep=glGetUniformLocation(blur,"u_step");
        glGenVertexArrays(1,vao,0);glBindVertexArray(vao[0]);glGenBuffers(1,vbo,0);glBindBuffer(GL_ARRAY_BUFFER,vbo[0]);
        float[] quad={-1,-1,0,0,1,-1,1,0,-1,1,0,1,-1,1,0,1,1,-1,1,0,1,1,1,1};
        FloatBuffer buffer=ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(quad).position(0);
        glBufferData(GL_ARRAY_BUFFER,96,buffer,GL_STATIC_DRAW);glEnableVertexAttribArray(0);glVertexAttribPointer(0,2,GL_FLOAT,false,16,0);
        glEnableVertexAttribArray(1);glVertexAttribPointer(1,2,GL_FLOAT,false,16,8);
        // Per layer: photo (mipmapped) and depth; then shared: blur scratch, quality target, neutral depth, wipe mask.
        int[] t=new int[8];glGenTextures(8,t,0);
        for(int i=0;i<8;i++){glBindTexture(GL_TEXTURE_2D,t[i]);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,i<2?GL_LINEAR_MIPMAP_LINEAR:GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);}
        layers[0].image=t[0];layers[1].image=t[1];layers[0].depth=t[2];layers[1].depth=t[3];scratch[0]=t[4];scratch[1]=t[5];neutralDepth=t[6];scratch[2]=t[7];
        glBindTexture(GL_TEXTURE_2D,neutralDepth);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,ByteBuffer.wrap(new byte[]{127,127,127,-1}));
        glBindTexture(GL_TEXTURE_2D,scratch[2]);glPixelStorei(GL_UNPACK_ALIGNMENT,1);glTexImage2D(GL_TEXTURE_2D,0,GL_R8,WIPE_W,WIPE_H,0,GL_RED,GL_UNSIGNED_BYTE,ByteBuffer.allocateDirect(WIPE_W*WIPE_H));
        glGenFramebuffers(3,fbo,0);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);check("initialize");
    }
    static String asset(Context c,String name)throws IOException{
        try(InputStream in=c.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
    }
    static int shader(int type,String source){int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);int[] ok=new int[1];glGetShaderiv(s,GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String log=glGetShaderInfoLog(s);glDeleteShader(s);throw new IllegalStateException(log);}return s;}
    static int program(String v,String f){int vs=shader(GL_VERTEX_SHADER,v),fs=shader(GL_FRAGMENT_SHADER,f),p=glCreateProgram();glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);glDeleteShader(vs);glDeleteShader(fs);int[] ok=new int[1];glGetProgramiv(p,GL_LINK_STATUS,ok,0);if(ok[0]==0){String log=glGetProgramInfoLog(p);glDeleteProgram(p);throw new IllegalStateException(log);}return p;}
    /** [program, uniform locations...] for an effect, compiled on first use. */
    int[] effect(String id)throws IOException{
        int[] cached=programs.get(id);if(cached!=null)return cached;
        int p=program(vertex,common+"\n"+asset(context,"fx/"+id+".glsl")+"\n"+main);
        int[] entry=new int[UNIFORMS.length+1];entry[0]=p;for(int i=0;i<UNIFORMS.length;i++)entry[i+1]=glGetUniformLocation(p,UNIFORMS[i]);
        programs.put(id,entry);return entry;
    }

    void upload(int index,Bitmap b){
        Layer l=layers[index];l.width=b.getWidth();l.height=b.getHeight();l.levels=(int)(Math.log(Math.max(l.width,l.height))/Math.log(2));
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,l.image);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAX_LEVEL,l.levels);GLUtils.texImage2D(GL_TEXTURE_2D,0,b,0);glGenerateMipmap(GL_TEXTURE_2D);l.nextLevel=1;l.hasDepth=false;check("upload");
    }
    void uploadDepth(int index,Bitmap b){glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,layers[index].depth);GLUtils.texImage2D(GL_TEXTURE_2D,0,b,0);glActiveTexture(GL_TEXTURE0);layers[index].hasDepth=true;check("depth upload");}
    boolean ready(int index){return layers[index].width>0;}
    boolean blurPending(){return layers[0].nextLevel<=layers[0].levels&&layers[0].width>0||layers[1].nextLevel<=layers[1].levels&&layers[1].width>0;}
    void updateWipe(ByteBuffer mask){glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,scratch[2]);glPixelStorei(GL_UNPACK_ALIGNMENT,1);mask.position(0);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,WIPE_W,WIPE_H,GL_RED,GL_UNSIGNED_BYTE,mask);glActiveTexture(GL_TEXTURE0);}

    void resize(int w,int h,float scale){
        renderWidth=Math.max(1,Math.round(w*scale));renderHeight=Math.max(1,Math.round(h*scale));
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,scratch[1]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,renderWidth,renderHeight,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo[1]);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,scratch[1],0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Quality framebuffer");glBindFramebuffer(GL_FRAMEBUFFER,0);
    }
    /** One separable Gaussian level of one layer per call, spread across frames. */
    boolean prepareBlur(){
        Layer l=layers[0].width>0&&layers[0].nextLevel<=layers[0].levels?layers[0]:layers[1];
        if(l.width==0||l.nextLevel>l.levels)return false;int level=l.nextLevel++,w=Math.max(1,l.width>>level),h=Math.max(1,l.height>>level);
        glBindVertexArray(vao[0]);glBindFramebuffer(GL_FRAMEBUFFER,fbo[0]);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,scratch[0]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,scratch[0],0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Blur framebuffer incomplete");
        glViewport(0,0,w,h);glUseProgram(blur);glUniform1i(blurSource,0);glBindTexture(GL_TEXTURE_2D,l.image);glUniform1f(blurLevel,level-1);glUniform2f(blurStep,1f/Math.max(1,l.width>>(level-1)),0);glDrawArrays(GL_TRIANGLES,0,6);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,l.image,level);glBindTexture(GL_TEXTURE_2D,scratch[0]);glUniform1f(blurLevel,0);glUniform2f(blurStep,0,1f/Math.max(1,l.height>>(level-1)));glDrawArrays(GL_TRIANGLES,0,6);
        glBindFramebuffer(GL_FRAMEBUFFER,0);check("blur");return blurPending();
    }

    /** The layer's external texture for a SurfaceTexture, created on first use so devices without OES video never touch it. */
    int videoTexture(int index){
        if(oes[index]!=0)return oes[index];int[] t=new int[1];glGenTextures(1,t,0);glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,t[0]);
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        check("video texture");return oes[index]=t[0];
    }
    /**
     * Copies the latched video frame into the layer's image at a capped size, then box-filters the mips in one call:
     * the per-level Gaussian pyramid would take a frame per level, longer than a video frame lives.
     */
    void blitVideo(int index,float[] transform,int videoWidth,int videoHeight)throws IOException{
        if(video==0){video=program(vertex,asset(context,"video_blit.glsl"));videoSource=glGetUniformLocation(video,"u_video");videoTransform=glGetUniformLocation(video,"u_transform");}
        Layer l=layers[index];int w=VideoPlayer.capped(videoWidth,videoHeight),h=VideoPlayer.capped(videoHeight,videoWidth);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,l.image);glBindFramebuffer(GL_FRAMEBUFFER,fbo[2]);
        boolean resized=l.width!=w||l.height!=h;
        if(resized){l.width=w;l.height=h;l.levels=(int)(Math.log(Math.max(w,h))/Math.log(2));glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAX_LEVEL,l.levels);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,null);}
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,l.image,0);
        if(resized&&glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Video framebuffer");
        glViewport(0,0,w,h);glBindVertexArray(vao[0]);glUseProgram(video);glDisable(GL_BLEND);
        glActiveTexture(GL_TEXTURE3);glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,oes[index]);glUniform1i(videoSource,3);glUniformMatrix4fv(videoTransform,1,false,transform,0);
        glDrawArrays(GL_TRIANGLES,0,6);glBindFramebuffer(GL_FRAMEBUFFER,0);
        glActiveTexture(GL_TEXTURE0);glGenerateMipmap(GL_TEXTURE_2D);l.nextLevel=l.levels+1;check("video blit");
    }

    void begin(){glBindFramebuffer(GL_FRAMEBUFFER,fbo[1]);glViewport(0,0,renderWidth,renderHeight);glBindVertexArray(vao[0]);}
    /** Draws one effect pass into the quality framebuffer; alpha below 1 blends it over the previous pass. */
    void draw(Pass pass,int w,int h,float x,float y,float motion,float[] touch,float time,float wake)throws IOException{
        Layer l=layers[pass.layer];int[] u=effect(pass.effect.id);glUseProgram(u[0]);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,l.image);glUniform1i(u[IMAGE+1],0);
        glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,l.hasDepth?l.depth:neutralDepth);glUniform1i(u[DEPTH+1],1);
        glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,scratch[2]);glUniform1i(u[WIPE+1],2);
        float aspect=(float)w/h,cover=aspect/((float)l.width/l.height),mm=w/pixelsPerMm;
        glUniform2f(u[IMAGE_SIZE+1],l.width,l.height);glUniform2f(u[COVER+1],Math.min(1,cover),Math.min(1,1/cover));glUniform2f(u[CROP+1],pass.cropX,pass.cropY);glUniform1f(u[ZOOM+1],pass.scale);
        glUniform1f(u[ASPECT+1],aspect);glUniform1f(u[SCREEN_MM+1],mm);
        // Classic's first slider scales the optical tilt; the projection is unused by other effects.
        float strength=pass.effect==Effects.ALL[0]?pass.a:.8f;
        FoldMath.projection(FoldMath.clamp(x,-80,80)*strength*motion,FoldMath.clamp(y,-80,80)*strength*motion,mm,aspect,projection);
        for(int i=0;i<5;i++)glUniform3fv(u[PROJECTION+1+i],1,projection,i*3);
        glUniform2f(u[TILT+1],FoldMath.clamp(x/65,-1,1),FoldMath.clamp(y/65,-1,1));glUniform4fv(u[TOUCH+1],1,touch,0);
        glUniform2f(u[PARAMS+1],pass.a,pass.b);glUniform1f(u[TOGGLE+1],pass.toggle);glUniform1f(u[MOTION+1],motion);
        glUniform1f(u[TIME+1],time);glUniform2f(u[WAKE+1],wake,wake<3?1:0);glUniform1f(u[ALPHA+1],pass.alpha);
        if(pass.alpha<.999f){glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);}
        glDrawArrays(GL_TRIANGLES,0,6);glDisable(GL_BLEND);
    }
    void end(int w,int h){
        glBindFramebuffer(GL_READ_FRAMEBUFFER,fbo[1]);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,0);
        glBlitFramebuffer(0,0,renderWidth,renderHeight,0,0,w,h,GL_COLOR_BUFFER_BIT,GL_LINEAR);
        glBindFramebuffer(GL_FRAMEBUFFER,0);
    }
    static void check(String at){int e=glGetError();if(e!=GL_NO_ERROR)throw new IllegalStateException(at+": GL "+Integer.toHexString(e));}
    void close(){
        for(int[] p:programs.values())glDeleteProgram(p[0]);programs.clear();glDeleteProgram(blur);
        glDeleteTextures(8,new int[]{layers[0].image,layers[1].image,layers[0].depth,layers[1].depth,scratch[0],scratch[1],neutralDepth,scratch[2]},0);
        for(int t:oes)if(t!=0)glDeleteTextures(1,new int[]{t},0);if(video!=0)glDeleteProgram(video);
        glDeleteFramebuffers(3,fbo,0);glDeleteBuffers(1,vbo,0);glDeleteVertexArrays(1,vao,0);
    }
}
