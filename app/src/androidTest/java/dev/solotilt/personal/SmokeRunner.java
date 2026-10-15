package dev.solotilt.personal;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Dependency-free test runner (the project is AndroidX-free). Runs every public void test*() method of SmokeTest and
 * reports each one with the status codes of the classic InstrumentationTestRunner (1 start, 0 ok, -2 failure), which
 * `am instrument -r` and Gradle's connectedAndroidTest understand.
 */
public final class SmokeRunner extends Instrumentation {
    static final int START=1,OK=0,FAILURE=-2;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        super.onStart();
        List<Method> tests=new ArrayList<>();
        for(Method m:SmokeTest.class.getDeclaredMethods())if(m.getName().startsWith("test")&&m.getParameterCount()==0&&Modifier.isPublic(m.getModifiers()))tests.add(m);
        tests.sort(Comparator.comparing(Method::getName));
        int failures=0,current=0;String cls=SmokeTest.class.getName();
        for(Method m:tests){
            Bundle status=new Bundle();status.putString("id","InstrumentationTestRunner");status.putString("class",cls);status.putString("test",m.getName());
            status.putInt("numtests",tests.size());status.putInt("current",++current);status.putString("stream","\n"+cls+":");
            sendStatus(START,new Bundle(status));
            Throwable error=null;SmokeTest test=new SmokeTest(this);
            try{test.setUp();m.invoke(test);}catch(InvocationTargetException e){error=e.getCause();}catch(Throwable e){error=e;}
            finally{try{test.tearDown();}catch(Throwable ignored){}}
            if(error==null){status.putString("stream",".");sendStatus(OK,status);}
            else{failures++;StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
                status.putString("stack",trace.toString());status.putString("stream","\nError in "+m.getName()+":\n"+trace);sendStatus(FAILURE,status);}
        }
        Bundle result=new Bundle();
        result.putString("stream","\n\nTest results for SmokeRunner="+(failures==0?"OK":"FAILURES")+"\nTests run: "+tests.size()+", Failures: "+failures+"\n");
        finish(Activity.RESULT_OK,result);
    }
}
