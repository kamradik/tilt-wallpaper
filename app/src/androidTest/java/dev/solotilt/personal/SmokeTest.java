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
        check(find(any("Движение","Motion"))!=null,"back in settings");
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
