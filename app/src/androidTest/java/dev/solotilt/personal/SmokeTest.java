package dev.solotilt.personal;

import android.accessibilityservice.AccessibilityService;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.util.regex.Pattern;

/**
 * UI smoke test over the real app: hub, catalog, search "лёд", settings, author, diagnostics. Each step leaves a PNG
 * in /sdcard/Android/data/dev.solotilt.personal/files/screenshots/ (pulled to build/screenshots by Gradle's
 * pullScreenshots task). The UI is driven through the accessibility tree, so it also checks that the custom views
 * expose usable labels. Labels match in Russian or English, whatever the in-app language is.
 */
public final class SmokeTest {
    static final String PKG="dev.solotilt.personal",DEVICE_SHOTS="/data/local/tmp/tilt-screenshots";static final long WAIT=20_000;
    final Instrumentation instrumentation;final UiAutomation ui;final Context app;final File shots;

    SmokeTest(Instrumentation instrumentation){
        this.instrumentation=instrumentation;ui=instrumentation.getUiAutomation();app=instrumentation.getTargetContext();
        shots=new File(app.getExternalFilesDir(null),"screenshots");
    }
    void setUp()throws Exception{
        shell("mkdir -p "+DEVICE_SHOTS);
        if(!shots.isDirectory()&&!shots.mkdirs())throw new IOException("Cannot create "+shots);
        shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        // A fresh emulator would open the first-launch onboarding; the hub test starts past it (testOnboarding covers it).
        ImageStore.prefs(app).edit().putBoolean(OnboardingActivity.SEEN,true).commit();
        launch();
    }
    /** Starts the app through the activity manager and waits until it is drawn: Context.startActivity from the
     *  instrumentation returned at once and, on a cold emulator, sometimes left the launcher in front. */
    void launch()throws IOException{
        check(app.getPackageManager().getLaunchIntentForPackage(PKG)!=null,"no launch intent");
        shell("am start -W --activity-clear-task -n "+PKG+"/.MainActivity");
    }
    void tearDown(){ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);}

    public void testOnboarding()throws Exception{
        ImageStore.prefs(app).edit().putBoolean(OnboardingActivity.SEEN,false).commit();launch();
        find(any("Продолжить","Continue"));pause(800);shot("00_onboarding");
        ImageStore.prefs(app).edit().putBoolean(OnboardingActivity.SEEN,true).commit();
    }
    public void testHubCatalogSearchSettingsAuthor()throws Exception{
        find(any("Настройки","Settings"));shot("01_hub");

        click(find(any("Все эффекты","All effects")));
        find(any("Поиск эффектов","Search effects"));shot("02_catalog");
        // The query goes in through ACTION_SET_TEXT, so no keyboard layout is involved.
        AccessibilityNodeInfo field=findClass("android.widget.EditText");
        Bundle text=new Bundle();text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"лёд");
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,text),"set text");
        find(Pattern.compile("лёд"));  // ACTION_SET_TEXT opens no keyboard, so no back here: it would close the catalog
        pause(800);shot("03_search_ice");
        AccessibilityNodeInfo close=findNow(any("Закрыть","Close"));if(close!=null)click(close);else back();

        click(find(any("Настройки","Settings")));
        find(any("Движение","Motion"));shot("04_settings");

        click(scrollTo(any("Автор — Kamradik","Author — Kamradik")));
        find(any("Аватарка Kamradik","Kamradik"));pause(600);shot("05_author");
        back();

        click(scrollTo(any("Диагностика","Diagnostics")));
        scrollTo(any("Журнал сбоев","Crash log"));pause(1200);shot("06_diagnostics");
        back();
        check(find(any("Диагностика","Diagnostics"))!=null,"back in settings");  // the list is still scrolled down to it
    }

    /** The second layer reads the composed frame the right way up: a photo red on top and blue below, Classic with
     *  Retro Film over it, must still be red at the top of the screen and blue at the bottom. */
    public void testSecondLayerKeepsTheFrameUpright()throws Exception{
        Thumbnails t=new Thumbnails(app);t.ensureGl();GlScene scene=t.scene;
        Bitmap photo=Bitmap.createBitmap(200,330,Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(photo);
        c.drawColor(0xff1030e0);android.graphics.Paint red=new android.graphics.Paint();red.setColor(0xffe02010);c.drawRect(0,0,200,165,red);
        scene.upload(0,photo);while(scene.prepareBlur()){}
        WallpaperConfig.Profile p=new WallpaperConfig.Profile();p.effect="classic";p.overlay="film";p.scale=1.02f;
        GlScene.Pass base=new GlScene.Pass(),top=new GlScene.Pass();base.set(0,p);check(top.setOverlay(0,p),"film is a layer effect");
        float[] touch={.5f,.5f,99,0};
        scene.begin();scene.draw(base,Thumbnails.WIDTH,Thumbnails.HEIGHT,0,0,1,touch,1,9,Thumbnails.PREVIEW_ENV);
        scene.overlay(top,Thumbnails.WIDTH,Thumbnails.HEIGHT,0,0,1,touch,1,9,Thumbnails.PREVIEW_ENV);scene.end(Thumbnails.WIDTH,Thumbnails.HEIGHT);
        java.nio.ByteBuffer px=java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder());
        android.opengl.GLES30.glReadPixels(Thumbnails.WIDTH/2,Thumbnails.HEIGHT-30,1,1,android.opengl.GLES30.GL_RGBA,android.opengl.GLES30.GL_UNSIGNED_BYTE,px); // GL rows count from the bottom
        int r1=px.get(0)&255,b1=px.get(2)&255;px.position(0);
        android.opengl.GLES30.glReadPixels(Thumbnails.WIDTH/2,30,1,1,android.opengl.GLES30.GL_RGBA,android.opengl.GLES30.GL_UNSIGNED_BYTE,px);
        int r2=px.get(0)&255,b2=px.get(2)&255;
        t.release();photo.recycle();
        check(r1>b1+60,"top of the screen should be red, got r="+r1+" b="+b1);
        check(b2>r2+60,"bottom of the screen should be blue, got r="+r2+" b="+b2);
    }
    /** Depth Anything runs on the device: a simple landscape (sky above, ground running toward the camera) must come
     *  out with the ground nearer (brighter) than the sky. */
    public void testDepthModelSeesTheGroundNearerThanTheSky()throws Exception{
        Bitmap b=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(b);
        android.graphics.Paint p=new android.graphics.Paint();
        p.setShader(new android.graphics.LinearGradient(0,0,0,220,0xff5b8fd6,0xffcfe3f5,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,640,220,p);
        p.setShader(new android.graphics.LinearGradient(0,220,0,480,0xff6b7a3a,0xff3c2f1c,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,220,640,480,p);
        p.setShader(null);p.setColor(0xff2a2a2a);for(int i=0;i<12;i++){float y=230+i*i*1.8f;c.drawRect(0,y,640,y+1+i*.6f,p);}  // furrows widening toward the viewer
        long t=SystemClock.elapsedRealtime();Bitmap d=MlDepth.estimate(app,b);t=SystemClock.elapsedRealtime()-t;
        android.util.Log.w("SoloDepth","Timing: Depth Anything x2 + guided refinement took "+t+" ms on this device"); // a warning, so ci/report.py shows it
        check(d.getWidth()==640&&d.getHeight()==480,"refined depth "+d.getWidth()+"x"+d.getHeight());
        float top=0,bottom=0;for(int x=0;x<d.getWidth();x++){top+=(d.getPixel(x,d.getHeight()/10)>>16)&255;bottom+=(d.getPixel(x,d.getHeight()*9/10)>>16)&255;}  // depth is in R
        check(bottom>top,"ground "+bottom/d.getWidth()+" should be nearer than sky "+top/d.getWidth()+" ("+t+" ms)");
        b.recycle();d.recycle();
    }
    /** Stage 4 pieces on a real system: the widget's views inflate, the app icon switches and back, and a look and
     *  the finishing modifiers survive saving the config. */
    public void testWidgetIconsAndLooks()throws Exception{
        android.widget.RemoteViews v=ParallaxWidget.views(app);check(v!=null&&v.getLayoutId()==R.layout.widget,"widget views");
        v.apply(app,new android.widget.FrameLayout(app));  // inflates the layout like a launcher would
        android.content.pm.PackageManager pm=app.getPackageManager();
        try{
            AppIcons.set(app,2);
            check(pm.getComponentEnabledSetting(AppIcons.component(app,2))==android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,"sunset icon enabled");
            check(pm.getComponentEnabledSetting(AppIcons.component(app,0))==android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,"dusk icon disabled");
            check(AppIcons.current(app)==2,"current icon");
            check(pm.getLaunchIntentForPackage(PKG)!=null,"still launchable with another icon");
        }finally{AppIcons.set(app,0);}
        check(AppIcons.current(app)==0,"icon restored");
        WallpaperConfig before=WallpaperConfig.load(app);String saved=before.json();
        try{
            WallpaperConfig c=WallpaperConfig.load(app);Looks.apply(app,c,Looks.ALL[0],Presets.HOME);c.home.mods[0]=.5f;c.save(app);
            WallpaperConfig back=WallpaperConfig.load(app);
            check(Looks.ALL[0].effect.equals(back.home.effect)&&Looks.ALL[0].overlay.equals(back.home.overlay),"look applied");
            check(Math.abs(back.home.mods[0]-.5f)<1e-4,"finishing saved");
        }finally{ImageStore.prefs(app).edit().putString(WallpaperConfig.KEY,saved).commit();}
    }
    /** Three photos become a series video of the expected length, which then imports as a scrubbed wallpaper clip. */
    public void testPhotoSeriesBecomesAVideo()throws Exception{
        java.util.List<android.net.Uri> uris=new java.util.ArrayList<>();
        for(int i=0;i<3;i++){Bitmap b=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888);b.eraseColor(0xff000000|(80*i)<<16|0x4080);
            File f=new File(app.getCacheDir(),"series"+i+".png");try(java.io.FileOutputStream o=new java.io.FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,o);}b.recycle();uris.add(android.net.Uri.fromFile(f));}
        File out=new File(app.getCacheDir(),"series-test.mp4");
        SeriesImport.encode(app,uris,out,null);
        try(android.media.MediaMetadataRetriever r=new android.media.MediaMetadataRetriever()){
            r.setDataSource(out.getPath());long ms=Long.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));
            long want=SeriesImport.frames(3)*1000L/SeriesImport.FPS;check(Math.abs(ms-want)<=120,"series length "+ms+" ms, want "+want);
        }
        out.delete();for(android.net.Uri u:uris)new File(u.getPath()).delete();
    }
    /** A look survives a round trip through a QR picture: encode, draw, read back with ZXing, decode. */
    public void testLookQrRoundTrip()throws Exception{
        Looks.Look look=Looks.ALL[3];Bitmap qr=LookCodes.qr(LookCodes.encode(look),600);
        Bitmap photo=Bitmap.createBitmap(900,1400,Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(photo);c.drawColor(0xff303036);c.drawBitmap(qr,150,400,null);  // a "screenshot" with the code on it
        Looks.Look back=LookCodes.decode(LookCodes.read(photo));
        check(back!=null&&back.effect.equals(look.effect)&&java.util.Objects.equals(back.overlay,look.overlay),"QR round trip");
        qr.recycle();photo.recycle();
    }
    /** U2-Net finds a clear subject: a red ball on a plain grey wall comes out with a mask (B = 255) that covers
     *  the ball and not the wall. */
    public void testSubjectMaskFindsTheBall()throws Exception{
        Bitmap b=Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(b);c.drawColor(0xff9a9a9e);
        android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.RadialGradient(210,300,150,0xffff5a40,0xffb01810,android.graphics.Shader.TileMode.CLAMP));c.drawCircle(240,330,140,p);
        Bitmap d=MlDepth.estimate(app,b);
        int centre=d.getPixel(d.getWidth()/2,d.getHeight()*330/640),corner=d.getPixel(10,10);
        check((centre&255)==255,"the map should carry a subject mask (B=255), got "+Integer.toHexString(centre));
        check(((centre>>8)&255)>200,"the ball should be in the mask, G="+((centre>>8)&255));
        check(((corner>>8)&255)<50,"the wall should not be in the mask, G="+((corner>>8)&255));
        check(((centre>>16)&255)>((corner>>16)&255),"the ball should be nearer than the wall");
        b.recycle();d.recycle();
    }
    /** The transcoder cuts on the exact frame: a clip whose frame i is grey level 5*i, keyframes 2 s apart, cut at
     *  1.0 s must start on frame 30 (grey 150), where a keyframe-based copy would start on frame 0. */
    public void testVideoTrimIsFrameExact()throws Exception{
        File source=new File(app.getCacheDir(),"ramp.mp4"),out=new File(app.getCacheDir(),"ramp-cut.mp4");
        writeRamp(source,90);
        float[] last={0};
        VideoTranscode.transcode(app,android.net.Uri.fromFile(source),1_000_000,2_500_000,out,f->last[0]=f);
        try(android.media.MediaMetadataRetriever r=new android.media.MediaMetadataRetriever()){
            r.setDataSource(out.getPath());
            long ms=Long.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));
            check(Math.abs(ms-1500)<=120,"cut length "+ms+" ms");
            check(grey(r,0)>=140&&grey(r,0)<=160,"first frame grey "+grey(r,0)+" (want 150: frame 30)");
            check(grey(r,500_000)>=215&&grey(r,500_000)<=235,"frame at 0.5 s grey "+grey(r,500_000)+" (want 225)");
        }
        check(last[0]>.9f,"progress reached "+last[0]);
        source.delete();out.delete();
    }
    static int grey(android.media.MediaMetadataRetriever r,long us){
        Bitmap b=r.getFrameAtTime(us,android.media.MediaMetadataRetriever.OPTION_CLOSEST);check(b!=null,"no frame at "+us);
        int c=b.getPixel(b.getWidth()/2,b.getHeight()/2);b.recycle();return ((c>>16&255)+(c>>8&255)+(c&255))/3;
    }
    /** Encodes frames of flat grey 5*i at 30 fps, 320x180, a keyframe every 2 s. */
    static void writeRamp(File file,int frames)throws Exception{
        android.media.MediaFormat f=android.media.MediaFormat.createVideoFormat("video/avc",320,180);
        f.setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT,android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(android.media.MediaFormat.KEY_BIT_RATE,2_000_000);f.setInteger(android.media.MediaFormat.KEY_FRAME_RATE,30);f.setInteger(android.media.MediaFormat.KEY_I_FRAME_INTERVAL,2);
        android.media.MediaCodec enc=android.media.MediaCodec.createEncoderByType("video/avc");enc.configure(f,null,null,android.media.MediaCodec.CONFIGURE_FLAG_ENCODE);
        android.view.Surface input=enc.createInputSurface();enc.start();
        android.media.MediaMuxer mux=new android.media.MediaMuxer(file.getPath(),android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        android.opengl.EGLDisplay d=android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY);int[] v=new int[2];android.opengl.EGL14.eglInitialize(d,v,0,v,1);
        android.opengl.EGLConfig[] cfg=new android.opengl.EGLConfig[1];int[] n=new int[1];
        android.opengl.EGL14.eglChooseConfig(d,new int[]{android.opengl.EGL14.EGL_RED_SIZE,8,android.opengl.EGL14.EGL_GREEN_SIZE,8,android.opengl.EGL14.EGL_BLUE_SIZE,8,android.opengl.EGL14.EGL_RENDERABLE_TYPE,android.opengl.EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,android.opengl.EGL14.EGL_NONE},0,cfg,0,1,n,0);
        android.opengl.EGLContext ctx=android.opengl.EGL14.eglCreateContext(d,cfg[0],android.opengl.EGL14.EGL_NO_CONTEXT,new int[]{android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION,2,android.opengl.EGL14.EGL_NONE},0);
        android.opengl.EGLSurface es=android.opengl.EGL14.eglCreateWindowSurface(d,cfg[0],input,new int[]{android.opengl.EGL14.EGL_NONE},0);
        android.opengl.EGL14.eglMakeCurrent(d,es,es,ctx);
        android.media.MediaCodec.BufferInfo info=new android.media.MediaCodec.BufferInfo();int track=-1;boolean eos=false;
        for(int i=0;i<=frames&&!eos;){
            if(i<frames){float g=Math.min(255,5*i)/255f;android.opengl.GLES20.glClearColor(g,g,g,1);android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT);
                android.opengl.EGLExt.eglPresentationTimeANDROID(d,es,i*1_000_000_000L/30);android.opengl.EGL14.eglSwapBuffers(d,es);}
            else enc.signalEndOfInputStream();
            i++;
            while(true){
                int o=enc.dequeueOutputBuffer(info,i>frames?10_000:0);
                if(o==android.media.MediaCodec.INFO_TRY_AGAIN_LATER){if(i>frames)continue;break;}
                if(o==android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=mux.addTrack(enc.getOutputFormat());mux.start();continue;}
                if(o<0)continue;
                java.nio.ByteBuffer data=enc.getOutputBuffer(o);
                if((info.flags&android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0&&info.size>0&&track>=0){data.position(info.offset).limit(info.offset+info.size);mux.writeSampleData(track,data,info);}
                enc.releaseOutputBuffer(o,false);
                if((info.flags&android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0){eos=true;break;}
            }
        }
        mux.stop();mux.release();enc.stop();enc.release();
        android.opengl.EGL14.eglMakeCurrent(d,android.opengl.EGL14.EGL_NO_SURFACE,android.opengl.EGL14.EGL_NO_SURFACE,android.opengl.EGL14.EGL_NO_CONTEXT);
        android.opengl.EGL14.eglDestroySurface(d,es);android.opengl.EGL14.eglDestroyContext(d,ctx);input.release();
    }

    // ---- small UiAutomation toolkit ----
    static Pattern any(String... labels){StringBuilder b=new StringBuilder();for(String l:labels)b.append(b.length()==0?"":"|").append(Pattern.quote(l));return Pattern.compile(b.toString(),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);} // section captions are upper case
    static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    static void pause(long ms){SystemClock.sleep(ms);}
    void back(){ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);pause(500);}
    void shell(String command)throws IOException{try(ParcelFileDescriptor out=ui.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(out)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}}
    void shot(String name)throws IOException{
        try{ui.waitForIdle(300,3000);}catch(Exception ignored){}pause(400);
        // screencap reads the composited display; UiAutomation.takeScreenshot returned stale, dotted frames over the GL
        // surface on the CI emulator. Writing to /data/local/tmp also survives the uninstall at the end of the run.
        shell("mkdir -p "+DEVICE_SHOTS);shell("screencap -p "+DEVICE_SHOTS+"/"+name+".png");
    }
    /** First node in the app's window whose text or content description contains the pattern. */
    AccessibilityNodeInfo findNow(Pattern p){AccessibilityNodeInfo root=ui.getRootInActiveWindow();return root==null?null:search(root,p,null);}
    AccessibilityNodeInfo find(Pattern p){
        long end=SystemClock.uptimeMillis()+WAIT;
        while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo n=findNow(p);if(n!=null)return n;pause(200);}
        throw new AssertionError("not on screen: "+p+"\nwindow tree:\n"+dump());
    }
    /** The active window's accessibility tree, compact, for failure messages. */
    String dump(){
        StringBuilder b=new StringBuilder();AccessibilityNodeInfo root=ui.getRootInActiveWindow();
        b.append("active window package: ").append(root==null?"none":root.getPackageName()).append('\n');
        for(android.view.accessibility.AccessibilityWindowInfo w:ui.getWindows())b.append("window: ").append(w.getTitle()).append(" active=").append(w.isActive()).append(" focused=").append(w.isFocused()).append('\n');
        dump(root,0,b);return b.length()>3000?b.substring(0,3000):b.toString();
    }
    static void dump(AccessibilityNodeInfo n,int depth,StringBuilder b){
        if(n==null||depth>14||b.length()>3000)return;
        CharSequence t=n.getText(),d=n.getContentDescription();String c=String.valueOf(n.getClassName());
        if(t!=null||d!=null||n.isClickable())b.append("  ".repeat(Math.min(depth,8))).append(c.substring(c.lastIndexOf('.')+1)).append(t!=null?" text="+t:"").append(d!=null?" desc="+d:"").append(n.isVisibleToUser()?"":" INVISIBLE").append(n.isClickable()?" click":"").append('\n');
        for(int i=0;i<n.getChildCount();i++)dump(n.getChild(i),depth+1,b);
    }
    AccessibilityNodeInfo findClass(String className){
        long end=SystemClock.uptimeMillis()+WAIT;
        while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo root=ui.getRootInActiveWindow();AccessibilityNodeInfo n=root==null?null:search(root,null,className);if(n!=null)return n;pause(200);}
        throw new AssertionError("no "+className);
    }
    static AccessibilityNodeInfo search(AccessibilityNodeInfo node,Pattern p,String className){
        if(node==null)return null;
        if(node.isVisibleToUser()){
            if(className!=null&&className.contentEquals(node.getClassName()==null?"":node.getClassName()))return node;
            if(p!=null&&(matches(p,node.getText())||matches(p,node.getContentDescription())))return node;
        }
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo hit=search(node.getChild(i),p,className);if(hit!=null)return hit;}
        return null;
    }
    static boolean matches(Pattern p,CharSequence s){return s!=null&&p.matcher(s).find();}
    /** Clicks the node or, for a label inside a row, the nearest clickable ancestor. */
    void click(AccessibilityNodeInfo node){
        for(AccessibilityNodeInfo n=node;n!=null;n=n.getParent())if(n.isClickable()){check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK),"click "+node.getText());pause(900);return;}
        throw new AssertionError("nothing clickable at "+node);
    }
    AccessibilityNodeInfo scrollTo(Pattern p){
        for(int i=0;i<15;i++){
            AccessibilityNodeInfo n=findNow(p);if(n!=null)return n;
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();AccessibilityNodeInfo list=root==null?null:scrollable(root);
            if(list==null||!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))break;pause(400);
        }
        return find(p);
    }
    static AccessibilityNodeInfo scrollable(AccessibilityNodeInfo node){
        if(node==null)return null;if(node.isScrollable())return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo s=scrollable(node.getChild(i));if(s!=null)return s;}
        return null;
    }
}
