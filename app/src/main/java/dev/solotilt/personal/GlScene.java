package dev.solotilt.personal;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import android.util.DisplayMetrics;
import java.nio.*;
import java.io.*;
import static android.opengl.GLES30.*;

final class GlScene {
    final int scene,blur; final int[] vao=new int[1],vbo=new int[1],textures=new int[4],fbo=new int[2];
    final float pixelsPerMm;
    final float[] projection=new float[15];final int[] locations=new int[10];
    int blurSource,blurLevel,blurStep;
    final int[] effectLocations=new int[12];int renderWidth,renderHeight;
    int width,height,levels,nextLevel;
    GlScene(Context context)throws IOException{
        DisplayMetrics dm=context.getResources().getDisplayMetrics();float dpi=(float)Math.sqrt(dm.xdpi*dm.ydpi);
        if(!Float.isFinite(dpi)||dpi<100||dpi>1000)dpi=160*dm.density;
        pixelsPerMm=dpi/25.4f;
        String vertex=asset(context,"quad.glsl");scene=program(vertex,asset(context,"effects.glsl"));blur=program(vertex,asset(context,"blur.glsl"));
        String[] names={"u_image","u_imageSize","u_coverScale","u_aspect","u_screenWidthMm","u_projectionX","u_projectionY","u_projectionZ","u_gap","u_blurGap"};
        for(int i=0;i<names.length;i++)locations[i]=glGetUniformLocation(scene,names[i]);
        blurSource=glGetUniformLocation(blur,"u_source");blurLevel=glGetUniformLocation(blur,"u_level");blurStep=glGetUniformLocation(blur,"u_step");
        String[] extra={"u_depth","u_crop","u_zoom","u_tilt","u_touch","u_params","u_previousParams","u_effect","u_previous","u_transition","u_motion","unused"};
        for(int i=0;i<extra.length;i++)effectLocations[i]=glGetUniformLocation(scene,extra[i]);
        glGenVertexArrays(1,vao,0);glBindVertexArray(vao[0]);glGenBuffers(1,vbo,0);glBindBuffer(GL_ARRAY_BUFFER,vbo[0]);
        float[] quad={-1,-1,0,0,1,-1,1,0,-1,1,0,1,-1,1,0,1,1,-1,1,0,1,1,1,1};
        FloatBuffer buffer=ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(quad).position(0);
        glBufferData(GL_ARRAY_BUFFER,96,buffer,GL_STATIC_DRAW);glEnableVertexAttribArray(0);glVertexAttribPointer(0,2,GL_FLOAT,false,16,0);
        glEnableVertexAttribArray(1);glVertexAttribPointer(1,2,GL_FLOAT,false,16,8);
        glGenTextures(4,textures,0);
        for(int i=0;i<4;i++){glBindTexture(GL_TEXTURE_2D,textures[i]);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,i==0?GL_LINEAR_MIPMAP_LINEAR:GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);}
        glBindTexture(GL_TEXTURE_2D,textures[2]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,ByteBuffer.wrap(new byte[]{127,127,127,-1}));
        glGenFramebuffers(2,fbo,0);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);check("initialize");
    }
    static String asset(Context c,String name)throws IOException{
        try(InputStream in=c.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
    }
    static int shader(int type,String source){int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);int[] ok=new int[1];glGetShaderiv(s,GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String log=glGetShaderInfoLog(s);glDeleteShader(s);throw new IllegalStateException(log);}return s;}
    static int program(String v,String f){int vs=shader(GL_VERTEX_SHADER,v),fs=shader(GL_FRAGMENT_SHADER,f),p=glCreateProgram();glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);glDeleteShader(vs);glDeleteShader(fs);int[] ok=new int[1];glGetProgramiv(p,GL_LINK_STATUS,ok,0);if(ok[0]==0){String log=glGetProgramInfoLog(p);glDeleteProgram(p);throw new IllegalStateException(log);}return p;}
    void upload(Bitmap b){width=b.getWidth();height=b.getHeight();levels=(int)(Math.log(Math.max(width,height))/Math.log(2));glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAX_LEVEL,levels);GLUtils.texImage2D(GL_TEXTURE_2D,0,b,0);glGenerateMipmap(GL_TEXTURE_2D);nextLevel=1;check("upload");}
    void uploadDepth(Bitmap b){glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,textures[2]);GLUtils.texImage2D(GL_TEXTURE_2D,0,b,0);glActiveTexture(GL_TEXTURE0);check("depth upload");}
    void resize(int w,int h,float scale){
        renderWidth=Math.max(1,Math.round(w*scale));renderHeight=Math.max(1,Math.round(h*scale));
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[3]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,renderWidth,renderHeight,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo[1]);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[3],0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Quality framebuffer");glBindFramebuffer(GL_FRAMEBUFFER,0);
    }
    boolean prepareBlur(){
        if(nextLevel>levels)return false;int level=nextLevel++,w=Math.max(1,width>>level),h=Math.max(1,height>>level);
        glBindVertexArray(vao[0]);glBindFramebuffer(GL_FRAMEBUFFER,fbo[0]);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[1]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[1],0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Blur framebuffer incomplete");
        glViewport(0,0,w,h);glUseProgram(blur);glUniform1i(blurSource,0);glBindTexture(GL_TEXTURE_2D,textures[0]);glUniform1f(blurLevel,level-1);glUniform2f(blurStep,1f/Math.max(1,width>>(level-1)),0);glDrawArrays(GL_TRIANGLES,0,6);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],level);glBindTexture(GL_TEXTURE_2D,textures[1]);glUniform1f(blurLevel,0);glUniform2f(blurStep,0,1f/Math.max(1,height>>(level-1)));glDrawArrays(GL_TRIANGLES,0,6);
        glBindFramebuffer(GL_FRAMEBUFFER,0);check("blur");return nextLevel<=levels;
    }
    void draw(int w,int h,float x,float y,WallpaperConfig config,int effect,int previous,float transition,float oldA,float oldB,float touchX,float touchY,float touchAge){
        glBindFramebuffer(GL_FRAMEBUFFER,fbo[1]);glViewport(0,0,renderWidth,renderHeight);glUseProgram(scene);glBindVertexArray(vao[0]);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);
        float aspect=(float)w/h,cover=aspect/((float)width/height),mm=w/pixelsPerMm;
        glUniform1i(locations[0],0);glUniform2f(locations[1],width,height);glUniform2f(locations[2],Math.min(1,cover),Math.min(1,1/cover));glUniform1f(locations[3],aspect);glUniform1f(locations[4],mm);
        float motion=config.reduceMotion?.15f:1;
        FoldMath.projection(FoldMath.clamp(x,-80,80)*config.a[0]*motion,FoldMath.clamp(y,-80,80)*config.a[0]*motion,mm,aspect,projection);for(int i=0;i<5;i++)glUniform3fv(locations[i+5],1,projection,i*3);
        glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,textures[2]);glUniform1i(effectLocations[0],1);
        glUniform2f(effectLocations[1],config.cropX,config.cropY);glUniform1f(effectLocations[2],config.scale);
        glUniform2f(effectLocations[3],FoldMath.clamp(x/65,-1,1),FoldMath.clamp(y/65,-1,1));
        glUniform4f(effectLocations[4],touchX,touchY,touchAge,config.touch&&!config.reduceMotion?1:0);
        glUniform2f(effectLocations[5],config.a[effect],config.b[effect]);glUniform2f(effectLocations[6],oldA,oldB);
        glUniform1i(effectLocations[7],effect);glUniform1i(effectLocations[8],previous);glUniform1f(effectLocations[9],transition);glUniform1f(effectLocations[10],motion);
        glDrawArrays(GL_TRIANGLES,0,6);
        glBindFramebuffer(GL_READ_FRAMEBUFFER,fbo[1]);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,0);
        glBlitFramebuffer(0,0,renderWidth,renderHeight,0,0,w,h,GL_COLOR_BUFFER_BIT,GL_LINEAR);
        glBindFramebuffer(GL_FRAMEBUFFER,0);
    }
    static void check(String at){int e=glGetError();if(e!=GL_NO_ERROR)throw new IllegalStateException(at+": GL "+Integer.toHexString(e));}
    void close(){glDeleteProgram(scene);glDeleteProgram(blur);glDeleteTextures(4,textures,0);glDeleteFramebuffers(2,fbo,0);glDeleteBuffers(1,vbo,0);glDeleteVertexArrays(1,vao,0);}
}
