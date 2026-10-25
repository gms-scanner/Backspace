package dev.goldforge.discovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.goldforge.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Server and dimension scoped observations. All mutation occurs on the client thread. */
public final class DiscoveryDatabase {
    public static final class Entry {
        public String id, type, name, icon, notes, reason, server;
        public int x, y, z, dimension, color, confidence;
        public long firstSeen, lastSeen;
        public boolean waypoint;
        public Map<String, Integer> counts = new LinkedHashMap<String, Integer>();

        public Entry copy() {
            Entry copy = new Entry();
            copy.id=id; copy.type=type; copy.name=name; copy.icon=icon; copy.notes=notes;
            copy.reason=reason; copy.server=server; copy.x=x; copy.y=y; copy.z=z;
            copy.dimension=dimension; copy.color=color; copy.confidence=confidence;
            copy.firstSeen=firstSeen; copy.lastSeen=lastSeen; copy.waypoint=waypoint;
            copy.counts=new LinkedHashMap<String,Integer>(counts==null?Collections.<String,Integer>emptyMap():counts);
            return copy;
        }
        public double distanceSq(double px, double py, double pz) {
            double dx=x-px,dy=y-py,dz=z-pz; return dx*dx+dy*dy+dz*dz;
        }
    }
    private static final class Document {
        int version=1;
        String server;
        int dimension;
        List<Entry> entries=new ArrayList<Entry>();
    }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_ENTRIES=10000;
    private static final LinkedHashMap<String,Entry> ENTRIES=new LinkedHashMap<String,Entry>();
    private static String context, server;
    private static int dimension;
    private static boolean dirty;
    private static long lastWrite;
    private DiscoveryDatabase() {}

    public static synchronized String currentServer() {
        Minecraft mc=Minecraft.getMinecraft();
        String profile=ConfigManager.currentServerProfileName();
        if(profile!=null&&!profile.trim().isEmpty())return profile;
        ServerData data=mc.getCurrentServerData();
        if(data!=null&&data.serverIP!=null&&!data.serverIP.trim().isEmpty())return data.serverIP;
        if(mc.world!=null&&mc.world.getWorldInfo()!=null)return "single:"+mc.world.getWorldInfo().getWorldName();
        return "unknown";
    }
    public static synchronized void activate() {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null)return;
        String wantedServer=currentServer();
        int wantedDimension=mc.world.provider.getDimension();
        String wanted=wantedServer+"|"+wantedDimension;
        if(wanted.equals(context))return;
        flush();
        ENTRIES.clear(); context=wanted; server=wantedServer; dimension=wantedDimension;
        dirty=false; lastWrite=System.currentTimeMillis();
        Path file=file();
        if(file==null||!Files.isRegularFile(file))return;
        try(Reader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            Document document=JSON.fromJson(reader,Document.class);
            if(document==null||document.version!=1||document.entries==null||document.entries.size()>MAX_ENTRIES
                    ||!wantedServer.equals(document.server)||wantedDimension!=document.dimension)return;
            for(Entry entry:document.entries)if(valid(entry))ENTRIES.put(entry.id,entry.copy());
        }catch(IOException|RuntimeException ignored){ENTRIES.clear();}
    }
    public static synchronized void worldChanged(){flush();ENTRIES.clear();context=null;server=null;dirty=false;}
    public static synchronized void tick(){activate();if(dirty&&System.currentTimeMillis()-lastWrite>=5000L)flush();}
    public static synchronized List<Entry> all(){activate();List<Entry> result=new ArrayList<Entry>(ENTRIES.size());for(Entry entry:ENTRIES.values())result.add(entry.copy());return result;}
    public static synchronized List<Entry> search(String query,String type,String sort,double px,double py,double pz){
        String needle=query==null?"":query.toLowerCase(Locale.ROOT);
        List<Entry> result=new ArrayList<Entry>();
        for(Entry entry:all())if((type==null||type.equalsIgnoreCase(entry.type))
                &&(needle.isEmpty()||(entry.name+" "+entry.notes+" "+entry.reason).toLowerCase(Locale.ROOT).contains(needle)))result.add(entry);
        Comparator<Entry> comparator;
        if("size".equalsIgnoreCase(sort))comparator=(a,b)->Integer.compare(total(b),total(a));
        else if("date".equalsIgnoreCase(sort))comparator=(a,b)->Long.compare(b.lastSeen,a.lastSeen);
        else if("significance".equalsIgnoreCase(sort))comparator=(a,b)->Integer.compare(b.confidence,a.confidence);
        else comparator=(a,b)->Double.compare(a.distanceSq(px,py,pz),b.distanceSq(px,py,pz));
        Collections.sort(result,comparator);return result;
    }
    public static synchronized Entry observe(String type,int x,int y,int z,int confidence,String reason,
                                             Map<String,Integer> counts,int mergeRadius,boolean waypoint) {
        activate();if(context==null||type==null)return null;
        int radius=Math.max(0,mergeRadius),bounded=Math.max(0,Math.min(100,confidence));
        Entry existing=null;
        for(Entry entry:ENTRIES.values())if(type.equals(entry.type)
                &&Math.abs(entry.x-x)<=radius&&Math.abs(entry.z-z)<=radius
                &&Math.abs(entry.y-y)<=Math.max(24,radius)) { existing=entry; break; }
        long now=System.currentTimeMillis();
        if(existing==null){
            if(ENTRIES.size()>=MAX_ENTRIES)return null;
            existing=new Entry();existing.id=UUID.randomUUID().toString();existing.type=type;
            existing.name=type+"_"+x+"_"+z;existing.icon=type.toLowerCase(Locale.ROOT);
            existing.notes="";existing.server=server;existing.dimension=dimension;
            existing.x=x;existing.y=y;existing.z=z;existing.firstSeen=now;
            existing.color=colorFor(type);ENTRIES.put(existing.id,existing);
        }
        existing.lastSeen=now;existing.confidence=Math.max(existing.confidence,bounded);
        existing.waypoint|=waypoint;existing.reason=reason==null?"":reason;
        if(counts!=null)for(Map.Entry<String,Integer> count:counts.entrySet())if(count.getKey()!=null&&count.getValue()!=null)
            existing.counts.put(count.getKey(),Math.max(existing.counts.containsKey(count.getKey())?existing.counts.get(count.getKey()):0,count.getValue()));
        dirty=true;return existing.copy();
    }
    public static synchronized boolean edit(String id,String name,String notes,int color,boolean waypoint){
        activate();Entry entry=ENTRIES.get(id);if(entry==null||name==null||name.length()>64||notes==null||notes.length()>1024)return false;
        entry.name=name;entry.notes=notes;entry.color=color;entry.waypoint=waypoint;dirty=true;return true;
    }
    public static synchronized boolean remove(String id){activate();if(ENTRIES.remove(id)==null)return false;dirty=true;return true;}
    public static synchronized boolean exportJson(String name){activate();return validName(name)&&write(exportFile(name),document());}
    public static synchronized int importJson(String name){
        activate();if(!validName(name))return -1;Path path=exportFile(name);if(!Files.isRegularFile(path))return -1;
        try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            Document document=JSON.fromJson(reader,Document.class);
            if(document==null||document.version!=1||document.entries==null||document.entries.size()>MAX_ENTRIES)return -1;
            int count=0;
            for(Entry entry:document.entries){
                if(!valid(entry)||!server.equals(entry.server)||dimension!=entry.dimension)continue;
                boolean duplicate=false;
                for(Entry known:ENTRIES.values())if(known.type.equals(entry.type)&&known.x==entry.x&&known.y==entry.y&&known.z==entry.z){duplicate=true;break;}
                if(!duplicate&&ENTRIES.size()<MAX_ENTRIES){Entry copy=entry.copy();copy.id=UUID.randomUUID().toString();ENTRIES.put(copy.id,copy);count++;}
            }
            if(count>0)dirty=true;return count;
        }catch(IOException|RuntimeException failure){return -1;}
    }
    public static synchronized void flush(){if(!dirty||context==null)return;if(write(file(),document())){dirty=false;lastWrite=System.currentTimeMillis();}}
    private static Document document(){Document document=new Document();document.server=server;document.dimension=dimension;for(Entry entry:ENTRIES.values())document.entries.add(entry.copy());return document;}
    private static boolean valid(Entry entry){return entry!=null&&entry.id!=null&&entry.type!=null&&entry.name!=null&&entry.server!=null&&entry.counts!=null
            &&entry.name.length()<=64&&entry.notes!=null&&entry.notes.length()<=1024&&entry.confidence>=0&&entry.confidence<=100;}
    private static int total(Entry entry){int count=0;for(Integer value:entry.counts.values())if(value!=null&&value>0)count+=value;return count;}
    private static int colorFor(String type){if("BASE".equals(type))return 0xFFFFD700;if("STASH".equals(type))return 0xFF64D5FF;if("PORTAL".equals(type))return 0xFFAA66FF;return 0xFFFFCC33;}
    private static boolean validName(String name){return name!=null&&name.matches("[A-Za-z0-9_-]{1,32}");}
    private static Path root(){return Minecraft.getMinecraft().mcDataDir.toPath().resolve("Backspace");}
    private static Path file(){return context==null?null:root().resolve("discoveries").resolve(UUID.nameUUIDFromBytes(server.getBytes(StandardCharsets.UTF_8)).toString()).resolve("dim"+dimension+".json");}
    private static Path exportFile(String name){return root().resolve("discovery-exports").resolve(name+".json");}
    private static boolean write(Path target,Document document){
        if(target==null)return false;Path temporary=target.resolveSibling(target.getFileName()+".tmp");
        try{Files.createDirectories(target.getParent());try(Writer writer=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)){JSON.toJson(document,writer);}
            try{Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}return true;
        }catch(IOException failure){try{Files.deleteIfExists(temporary);}catch(IOException ignored){}return false;}
    }
}
