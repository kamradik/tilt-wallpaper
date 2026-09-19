package dev.solotilt.personal;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import android.util.DisplayMetrics;
import java.nio.*;
import java.io.*;
import java.util.*;
import static android.opengl.GLES30.*;

final class GlScene {
    final int scene,blur; final int[] vao=new int[1],vbo=new int[1],textures=new int[2],fbo=new int[1];
    final Map<String,Integer> uniforms=new HashMap<>(); final float pixelsPerMm;
    int width,height,levels,nextLevel;
    GlScene(Context context)throws IOException{
        DisplayMetrics dm=context.getResources().getDisplayMetrics();float dpi=(float)Math.sqrt(dm.xdpi*dm.ydpi);
        if(!Float.isFinite(dpi)||dpi<100||dpi>1000)dpi=160*dm.density;
        pixelsPerMm=dpi/25.4f;
        String vertex=asset(context,"quad.glsl");scene=program(vertex,asset(context,"fold.glsl"));blur=program(vertex,asset(context,"blur.glsl"));
        glGenVertexArrays(1,vao,0);glBindVertexArray(vao[0]);glGenBuffers(1,vbo,0);glBindBuffer(GL_ARRAY_BUFFER,vbo[0]);
        float[] quad={-1,-1,0,0,1,-1,1,0,-1,1,0,1,-1,1,0,1,1,-1,1,0,1,1,1,1};
        FloatBuffer buffer=ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(quad).position(0);
        glBufferData(GL_ARRAY_BUFFER,96,buffer,GL_STATIC_DRAW);glEnableVertexAttribArray(0);glVertexAttribPointer(0,2,GL_FLOAT,false,16,0);
        glEnableVertexAttribArray(1);glVertexAttribPointer(1,2,GL_FLOAT,false,16,8);
        glGenTextures(2,textures,0);
        for(int i=0;i<2;i++){glBindTexture(GL_TEXTURE_2D,textures[i]);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,i==0?GL_LINEAR_MIPMAP_LINEAR:GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);}
        glGenFramebuffers(1,fbo,0);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);check("initialize");
    }
    static String asset(Context c,String name)throws IOException{
        try(InputStream in=c.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
    }
    static int shader(int type,String source){int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);int[] ok=new int[1];glGetShaderiv(s,GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String log=glGetShaderInfoLog(s);glDeleteShader(s);throw new IllegalStateException(log);}return s;}
    static int program(String v,String f){int vs=shader(GL_VERTEX_SHADER,v),fs=shader(GL_FRAGMENT_SHADER,f),p=glCreateProgram();glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);glDeleteShader(vs);glDeleteShader(fs);int[] ok=new int[1];glGetProgramiv(p,GL_LINK_STATUS,ok,0);if(ok[0]==0){String log=glGetProgramInfoLog(p);glDeleteProgram(p);throw new IllegalStateException(log);}return p;}
    int u(int p,String name){String key=p+name;Integer location=uniforms.get(key);if(location==null){location=glGetUniformLocation(p,name);uniforms.put(key,location);}return location;}
    void upload(Bitmap b){width=b.getWidth();height=b.getHeight();levels=(int)(Math.log(Math.max(width,height))/Math.log(2));glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAX_LEVEL,levels);GLUtils.texImage2D(GL_TEXTURE_2D,0,b,0);glGenerateMipmap(GL_TEXTURE_2D);nextLevel=1;check("upload");}
    boolean prepareBlur(){
        if(nextLevel>levels)return false;int level=nextLevel++,w=Math.max(1,width>>level),h=Math.max(1,height>>level);
        glBindVertexArray(vao[0]);glBindFramebuffer(GL_FRAMEBUFFER,fbo[0]);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[1]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[1],0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Blur framebuffer incomplete");
        glViewport(0,0,w,h);glUseProgram(blur);glUniform1i(u(blur,"u_source"),0);glBindTexture(GL_TEXTURE_2D,textures[0]);glUniform1f(u(blur,"u_level"),level-1);glUniform2f(u(blur,"u_step"),1f/Math.max(1,width>>(level-1)),0);glDrawArrays(GL_TRIANGLES,0,6);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[0],level);glBindTexture(GL_TEXTURE_2D,textures[1]);glUniform1f(u(blur,"u_level"),0);glUniform2f(u(blur,"u_step"),0,1f/Math.max(1,height>>(level-1)));glDrawArrays(GL_TRIANGLES,0,6);
        glBindFramebuffer(GL_FRAMEBUFFER,0);check("blur");return nextLevel<=levels;
    }
    void draw(int w,int h,float x,float y){
        glBindFramebuffer(GL_FRAMEBUFFER,0);glViewport(0,0,w,h);glUseProgram(scene);glBindVertexArray(vao[0]);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);
        float aspect=(float)w/h,cover=aspect/((float)width/height),mm=w/pixelsPerMm;
        glUniform1i(u(scene,"u_image"),0);glUniform2f(u(scene,"u_imageSize"),width,height);glUniform2f(u(scene,"u_coverScale"),Math.min(1,cover),Math.min(1,1/cover));glUniform1f(u(scene,"u_aspect"),aspect);glUniform1f(u(scene,"u_screenWidthMm"),mm);
        float[] p=FoldMath.projection(x,y,mm,aspect);String[] names={"u_projectionX","u_projectionY","u_projectionZ","u_gap","u_blurGap"};for(int i=0;i<5;i++)glUniform3fv(u(scene,names[i]),1,p,i*3);
        glDrawArrays(GL_TRIANGLES,0,6);check("draw");
    }
    static void check(String at){int e=glGetError();if(e!=GL_NO_ERROR)throw new IllegalStateException(at+": GL "+Integer.toHexString(e));}
    void close(){glDeleteProgram(scene);glDeleteProgram(blur);glDeleteTextures(2,textures,0);glDeleteFramebuffers(1,fbo,0);glDeleteBuffers(1,vbo,0);glDeleteVertexArrays(1,vao,0);}
}
