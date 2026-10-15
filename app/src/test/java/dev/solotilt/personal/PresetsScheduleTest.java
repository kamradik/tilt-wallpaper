package dev.solotilt.personal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresetsScheduleTest {
    // ---------------------------------------------------------------- config v4
    @Test public void v3ConfigMigratesToV4WithEmptyPresetsAndSchedule()throws Exception{
        String v3="{\"version\":3,\"separateLock\":true,\"favorites\":[\"pond\"],\"home\":{\"effect\":\"prism\",\"cropX\":0.3,\"params\":{\"prism\":[0.2,0.9,0]}},\"lock\":{\"effect\":\"aurora\"}}";
        WallpaperConfig c=WallpaperConfig.parse(v3);
        assertEquals("prism",c.home.effect);assertEquals(.3f,c.home.cropX,0);assertArrayEquals(new float[]{.2f,.9f,0},c.home.current(),0);
        assertEquals("aurora",c.lock.effect);assertTrue(c.separateLock&&c.isFavorite("pond"));
        assertTrue(c.presets.isEmpty());assertFalse(c.homePlan.enabled||c.homePlan.rotate);assertTrue(c.homePlan.windows.isEmpty()&&c.lockPlan.windows.isEmpty());
        JSONObject saved=new JSONObject(c.json());
        assertEquals(4,saved.getInt("version"));assertTrue(saved.has("presets")&&saved.has("schedule"));
        assertEquals(c.json(),WallpaperConfig.parse(c.json()).json());
        // v3 files never carry presets; even if one did, they are only read from v4 on.
        assertTrue(WallpaperConfig.parse("{\"version\":3,\"presets\":[{\"id\":\"abcd1234\",\"name\":\"x\"}]}").presets.isEmpty());
    }
    @Test public void presetsAndScheduleRoundTrip()throws Exception{
        WallpaperConfig c=new WallpaperConfig();
        WallpaperConfig.Preset p=new WallpaperConfig.Preset();p.id="abc123xyz";p.name="Вечер";p.media=WallpaperConfig.Preset.PHOTO;p.created=42;
        p.profile.effect="golden";p.profile.params("golden")[0]=.33f;p.profile.params("ice")[1]=.71f;p.profile.cropY=.2f;p.profile.scale=1.5f;
        WallpaperConfig.Preset q=new WallpaperConfig.Preset();q.id="zzzz0001";q.name="Утро";q.builtin=2;
        c.presets.add(p);c.presets.add(q);
        c.homePlan.enabled=true;c.homePlan.windows.add(new Schedule.Window(19*60,p.id));c.homePlan.windows.add(new Schedule.Window(7*60,q.id));c.homePlan.sort();
        c.lockPlan.rotate=true;c.lockPlan.perUnlock=true;c.lockPlan.album="content://com.android.externalstorage.documents/tree/primary%3APictures";
        WallpaperConfig copy=WallpaperConfig.parse(c.json());
        assertEquals(2,copy.presets.size());WallpaperConfig.Preset r=copy.preset("abc123xyz");
        assertEquals("Вечер",r.name);assertEquals(WallpaperConfig.Preset.PHOTO,r.media);assertEquals(42,r.created);
        assertEquals("golden",r.profile.effect);assertEquals(.33f,r.profile.params("golden")[0],0);assertEquals(.71f,r.profile.params("ice")[1],0);
        assertEquals(.2f,r.profile.cropY,0);assertEquals(1.5f,r.profile.scale,0);
        assertEquals(2,copy.preset("zzzz0001").builtin);assertEquals(WallpaperConfig.Preset.BUILTIN,copy.preset("zzzz0001").media);
        assertTrue(copy.homePlan.enabled);assertEquals(List.of(7*60,19*60),List.of(copy.homePlan.windows.get(0).minute,copy.homePlan.windows.get(1).minute));
        assertEquals("zzzz0001",copy.homePlan.windows.get(0).preset);
        assertTrue(copy.lockPlan.rotate&&copy.lockPlan.perUnlock&&copy.lockPlan.rotating());assertEquals(c.lockPlan.album,copy.lockPlan.album);
        // A copy of a preset does not share parameter arrays.
        WallpaperConfig.Preset dup=r.copy();dup.profile.params("golden")[0]=.9f;assertEquals(.33f,r.profile.params("golden")[0],0);
    }
    @Test public void presetJsonIsClamped()throws Exception{
        String name="  a\u0000b\nc   "+"x".repeat(80);
        String json="{\"version\":4,\"presets\":[{\"id\":\"../../evil\",\"name\":"+JSONObject.quote(name)+",\"media\":\"exe\",\"builtin\":7,\"created\":-5,"
            +"\"profile\":{\"effect\":\"nope\",\"cropX\":-3,\"cropY\":\"NaN\",\"scale\":99,\"params\":{\"light\":[9,-3,0.2],\"matrix\":[0.5,0.5]}}},"
            +"null,{\"id\":\"dupe0001\"},{\"id\":\"dupe0001\"}],"
            +"\"schedule\":{\"home\":{\"enabled\":true,\"album\":\"file:///sdcard\",\"windows\":[{\"at\":-1,\"preset\":\"dupe0001\"},{\"at\":3000,\"preset\":\"dupe0001\"},{\"at\":60,\"preset\":\"../x\"},"
            +"{\"at\":60,\"preset\":\"dupe0001\"},{\"at\":60,\"preset\":\"dupe0001\"},{\"at\":120,\"preset\":\"gone0001\"}]}}}";
        WallpaperConfig c=WallpaperConfig.parse(json);
        assertEquals(2,c.presets.size());
        WallpaperConfig.Preset p=c.presets.get(0);
        assertTrue(WallpaperConfig.validId(p.id));assertNotEquals("../../evil",p.id);
        assertTrue(p.name.length()<=40);assertFalse(p.name.contains("\n")||p.name.contains("\u0000"));assertTrue(p.name.startsWith("a b c"));
        assertEquals(WallpaperConfig.Preset.BUILTIN,p.media);assertEquals(1,p.builtin);assertEquals(0,p.created);
        assertEquals("classic",p.profile.effect);assertEquals(0,p.profile.cropX,0);assertEquals(.5f,p.profile.cropY,0);assertEquals(2.5f,p.profile.scale,0);
        assertArrayEquals(new float[]{1,0,0},p.profile.params("light"),0);assertFalse(p.profile.params.containsKey("matrix"));
        assertEquals("dupe0001",c.presets.get(1).id);
        // Invalid minutes, bad ids, duplicate times and windows of deleted presets are dropped; a file:// album is refused.
        assertEquals(1,c.homePlan.windows.size());assertEquals(60,c.homePlan.windows.get(0).minute);assertNull(c.homePlan.album);
        assertEquals("Пресет",WallpaperConfig.cleanName(" \t "));
    }
    @Test public void presetCountIsCapped()throws Exception{
        JSONArray list=new JSONArray();for(int i=0;i<100;i++)list.put(new JSONObject(Map.of("id","p"+(1000+i),"name","n"+i)));
        assertEquals(WallpaperConfig.MAX_PRESETS,WallpaperConfig.parse("{\"version\":4,\"presets\":"+list+"}").presets.size());
    }

    // ---------------------------------------------------------------- schedule
    static List<Schedule.Window> windows(int... minutes){List<Schedule.Window> w=new ArrayList<>();for(int m:minutes)w.add(new Schedule.Window(m,"p"+m+"aaaa"));w.sort(Comparator.comparingInt(x->x.minute));return w;}
    @Test public void activeWindowWrapsAcrossMidnight()throws Exception{
        List<Schedule.Window> w=windows(7*60,19*60);
        assertEquals(1,Schedule.active(w,6*60+59));  // before the first window: still last night's
        assertEquals(0,Schedule.active(w,7*60));assertEquals(0,Schedule.active(w,18*60+59));
        assertEquals(1,Schedule.active(w,19*60));assertEquals(1,Schedule.active(w,23*60+59));assertEquals(1,Schedule.active(w,0));
        assertEquals(-1,Schedule.active(new ArrayList<>(),600));
        assertEquals(0,Schedule.active(windows(22*60),3*60));
        List<Schedule.Window> four=windows(0,6*60,12*60,18*60);for(int m=0;m<Schedule.DAY;m+=37)assertEquals(m/360,Schedule.active(four,m));
    }
    @Test public void windowKeyChangesOncePerOccurrence()throws Exception{
        List<Schedule.Window> w=windows(7*60,19*60);LocalDate day=LocalDate.of(2026,3,10);
        String night=Schedule.windowKey(w,day.atTime(23,0)),early=Schedule.windowKey(w,day.plusDays(1).atTime(2,0));
        assertEquals(night,early);  // 19:00 window that began yesterday is the same occurrence after midnight
        assertTrue(night.startsWith("2026-03-10@1140"));
        String morning=Schedule.windowKey(w,day.plusDays(1).atTime(7,0));assertNotEquals(early,morning);assertTrue(morning.startsWith("2026-03-11@420"));
        assertEquals(morning,Schedule.windowKey(w,day.plusDays(1).atTime(18,59)));
        assertNotEquals(Schedule.windowKey(w,day.atTime(8,0)),Schedule.windowKey(w,day.plusDays(1).atTime(8,0)));  // same window, next day
        assertNull(Schedule.windowKey(new ArrayList<>(),day.atTime(8,0)));
        Schedule.Plan p=new Schedule.Plan();assertEquals("d2026-03-10",Schedule.rotationKey(p,day,5));p.perUnlock=true;assertEquals("u5",Schedule.rotationKey(p,day,5));
        assertEquals("07:05",Schedule.hhmm(425));
    }

    // ---------------------------------------------------------------- .tilt files
    static byte[] zip(Object... nameAndData)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(out)){for(int i=0;i<nameAndData.length;i+=2){z.putNextEntry(new ZipEntry((String)nameAndData[i]));z.write((byte[])nameAndData[i+1]);z.closeEntry();}}
        return out.toByteArray();
    }
    static byte[] presetJson(String effect)throws JSONException{
        return new JSONObject().put("format","tilt-preset").put("version",1).put("name","Shared").put("media","photo")
            .put("profile",new JSONObject().put("effect",effect).put("scale",1.3).put("params",new JSONObject().put("holo",new JSONArray().put(.4).put(.6).put(1)))).toString().getBytes(StandardCharsets.UTF_8);
    }
    static void rejects(byte[] file){try{Presets.read(new ByteArrayInputStream(file));fail("accepted a bad .tilt file");}catch(IOException expected){}}
    @Test public void tiltFileRoundTripsAndValidates()throws Exception{
        byte[] photo=new byte[5000];new Random(1).nextBytes(photo);
        Presets.Tilt t=Presets.read(new ByteArrayInputStream(zip("preset.json",presetJson("holo"),"photo.jpg",photo,"readme.txt","hi".getBytes(StandardCharsets.UTF_8))));
        assertArrayEquals(photo,t.photo);
        WallpaperConfig.Preset p=Presets.parse(t.json,true);
        assertEquals("holo",p.profile.effect);assertEquals(1.3f,p.profile.scale,0);assertEquals(.4f,p.profile.params("holo")[0],0);
        assertEquals(WallpaperConfig.Preset.PHOTO,p.media);assertTrue(WallpaperConfig.validId(p.id));
        assertEquals(WallpaperConfig.Preset.BUILTIN,Presets.parse(t.json,false).media);  // claims a photo it does not carry
        assertEquals("classic",Presets.parse(presetJson("rm -rf"),true).profile.effect);  // unknown effect
        // The exported JSON parses back to the same look.
        WallpaperConfig.Preset back=Presets.parse(Presets.exportJson(p).toString().getBytes(StandardCharsets.UTF_8),true);
        assertEquals(WallpaperConfig.writeProfile(p.profile).toString(),WallpaperConfig.writeProfile(back.profile).toString());
        WallpaperConfig.Preset named=back.copy();named.name="a/b?";assertEquals("a_b_.tilt",Presets.fileName(named));
    }
    @Test public void tiltImportRejectsZipSlipBombsAndJunk()throws Exception{
        byte[] json=presetJson("holo");
        rejects(zip("../preset.json",json));
        rejects(zip("preset.json",json,"../../shared_prefs/wallpaper.xml",new byte[1]));
        rejects(zip("/preset.json",json));
        rejects(zip("dir\\..\\preset.json",json));
        // Duplicate entry: ZipOutputStream refuses to write one, so the second name is patched in afterwards.
        byte[] dup=zip("preset.json",json,"preset.jsoz",json);String latin=new String(dup,StandardCharsets.ISO_8859_1).replace("preset.jsoz","preset.json");
        rejects(latin.getBytes(StandardCharsets.ISO_8859_1));
        rejects(zip("preset.json",new byte[Presets.MAX_JSON+1]));                           // oversized JSON (zeros compress tiny)
        rejects(zip("preset.json",json,"photo.jpg",new byte[Presets.MAX_PHOTO+1]));         // oversized photo
        Object[] many=new Object[2*(Presets.MAX_ENTRIES+1)];for(int i=0;i<many.length;i+=2){many[i]="f"+i;many[i+1]=new byte[1];}
        rejects(zip(many));                                                                 // too many entries
        rejects(zip("photo.jpg",new byte[10]));                                             // no preset.json
        rejects("definitely not a zip".getBytes(StandardCharsets.UTF_8));
        rejects(new byte[0]);
        try{Presets.parse("{\"format\":\"other\",\"profile\":{}}".getBytes(StandardCharsets.UTF_8),false);fail();}catch(IOException expected){}
        try{Presets.parse("[1,2]".getBytes(StandardCharsets.UTF_8),false);fail();}catch(IOException expected){}
        try{Presets.parse("{\"format\":\"tilt-preset\"}".getBytes(StandardCharsets.UTF_8),false);fail();}catch(IOException expected){}
    }

    // ---------------------------------------------------------------- collections and surprise
    @Test public void collectionsFollowFlagsAndCuratedIds()throws Exception{
        for(Set<String> curated:List.of(WallpaperConfig.CALM,WallpaperConfig.VIVID))for(String id:curated)assertEquals("unknown id "+id,id,Effects.get(id).id);
        for(int k=0;k<WallpaperConfig.COLLECTION_RU.length;k++){
            int n=0;for(Effects.Effect e:Effects.ALL)if(WallpaperConfig.inCollection(k,e))n++;assertTrue("empty collection "+k,n>=3);
        }
        for(Effects.Effect e:Effects.ALL){
            assertEquals(e.has(Effects.DEPTH),WallpaperConfig.inCollection(3,e));
            if(WallpaperConfig.inCollection(2,e))assertFalse(e.id,e.animated(true)||e.has(Effects.DEPTH));
        }
        assertTrue(WallpaperConfig.inCollection(2,Effects.get("classic")));assertFalse(WallpaperConfig.inCollection(2,Effects.get("aurora")));
        assertFalse(WallpaperConfig.inCollection(2,Effects.get("holo")));assertFalse(WallpaperConfig.inCollection(9,Effects.get("classic")));
        assertEquals(WallpaperConfig.COLLECTION_RU.length,WallpaperConfig.COLLECTION_EN.length);
    }
    @Test public void surprisePicksAnotherEffectWithTastefulParams()throws Exception{
        Random r=new Random(7);Set<String> seen=new HashSet<>();String current="classic";
        for(int i=0;i<500;i++){
            float[] v=new float[3];String next=WallpaperConfig.surprise(r,current,v);
            assertNotEquals(current,next);assertEquals(next,Effects.get(next).id);
            assertTrue(v[0]>=.3f&&v[0]<=.8f&&v[1]>=.3f&&v[1]<=.8f);assertTrue(v[2]==0||v[2]==1);
            if(!Effects.get(next).hasSwitch())assertEquals(1,v[2],0);
            seen.add(next);current=next;
        }
        assertEquals(Effects.ALL.length,seen.size());
        assertNotEquals("holo",WallpaperConfig.surprise(new Random(3),"sheen",new float[3]));  // a renamed id counts as its effect
    }
}
