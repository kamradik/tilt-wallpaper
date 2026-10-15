package dev.solotilt.personal;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Offline crash log: files/crash.log, rotated to crash.log.1 past ~256 KB (two files kept). Nothing leaves the device
 * unless the user shares it from the diagnostics screen. Uncaught exceptions are written, then passed on to the
 * previous handler, so the platform still shows its crash dialog and kills the process.
 */
final class CrashLog {
    static final String NAME="crash.log";static final long LIMIT=256*1024;static final int MAX_STACK_LINES=60;
    private static volatile File dir;private static volatile String header="";

    /** Called once from TiltApp.onCreate. */
    static synchronized void install(Context c){
        if(dir!=null)return;dir=c.getFilesDir();
        String version="?";long code=0;
        try{android.content.pm.PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);version=p.versionName;code=p.getLongVersionCode();}catch(Exception ignored){}
        header=deviceLine(version,code,Build.MANUFACTURER,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT);
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{
            try{append(report("CRASH",System.currentTimeMillis(),header,t.getName(),e));}catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(t,e);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        });
    }
    /** Non-fatal problem worth keeping (renderer errors and the like). Any thread; never throws. */
    static void note(String message){note(message,null);}
    static void note(String message,Throwable error){
        if(dir==null)return;
        try{append(error==null?entry("NOTE",System.currentTimeMillis(),header,Thread.currentThread().getName(),message):report("NOTE: "+message,System.currentTimeMillis(),header,Thread.currentThread().getName(),error));}catch(Throwable ignored){}
    }
    /** Older file first, so the text reads in time order. Empty when nothing was logged. */
    static synchronized String read(){
        if(dir==null)return "";StringBuilder out=new StringBuilder();
        for(File f:new File[]{new File(dir,NAME+".1"),new File(dir,NAME)})if(f.isFile())try{out.append(new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}catch(IOException ignored){}
        return out.toString();
    }
    static synchronized void clear(){if(dir==null)return;new File(dir,NAME).delete();new File(dir,NAME+".1").delete();}
    static synchronized void append(String text)throws IOException{if(dir!=null)append(dir,text,LIMIT);}

    // ---- pure helpers (unit-tested on the JVM) ----
    static void append(File folder,String text,long limit)throws IOException{
        File log=new File(folder,NAME);
        if(log.length()>0&&log.length()+text.length()>limit){File old=new File(folder,NAME+".1");if(old.exists()&&!old.delete())throw new IOException("Cannot rotate "+old);if(!log.renameTo(old))throw new IOException("Cannot rotate "+log);}
        try(Writer w=new OutputStreamWriter(new FileOutputStream(log,true),StandardCharsets.UTF_8)){w.write(text);}
    }
    static String deviceLine(String version,long code,String maker,String model,String release,int sdk){
        return String.format(Locale.ROOT,"Tilt %s (%d) · %s %s · Android %s (API %d)",version,code,maker,model,release,sdk);
    }
    static String stamp(long time){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT);return f.format(new Date(time));}
    static String entry(String kind,long time,String device,String thread,String body){
        return "=== "+kind+" · "+stamp(time)+" ===\n"+device+"\nThread: "+thread+"\n"+(body==null?"":body)+"\n\n";
    }
    /** Compact stack: each throwable in the cause chain with its frames, capped so a deep recursion cannot flood the file. */
    static String report(String kind,long time,String device,String thread,Throwable e){
        StringBuilder b=new StringBuilder();int lines=0;Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable t=e;t!=null&&seen.add(t)&&lines<MAX_STACK_LINES;t=t.getCause()){
            if(t!=e)b.append("Caused by: ");b.append(t).append('\n');lines++;
            StackTraceElement[] frames=t.getStackTrace();
            for(int i=0;i<frames.length&&lines<MAX_STACK_LINES;i++,lines++)b.append("  at ").append(frames[i]).append('\n');
        }
        if(lines>=MAX_STACK_LINES)b.append("  …\n");
        return entry(kind,time,device,thread,b.toString().trim());
    }
    /** The share sheet passes text through a Binder transaction; keep the newest part and say what was cut. */
    static String forSharing(String log,int max){
        if(log.length()<=max)return log;String note="[… older entries cut]\n";return note+log.substring(log.length()-(max-note.length()));
    }
    private CrashLog(){}
}
