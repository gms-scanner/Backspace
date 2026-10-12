package dev.goldforge;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Properties;

/** Small persistent waypoint store, scoped to this Minecraft installation. */
public final class WaypointManager {
    public static final class Waypoint {
        public final String name;
        public final int x,y,z,dimension,color;
        private Waypoint(String name,int x,int y,int z,int dimension,int color){this.name=name;this.x=x;this.y=y;this.z=z;this.dimension=dimension;this.color=color;}
        public BlockPos position(){return new BlockPos(x,y,z);}
    }
    private static final Properties VALUES=new Properties();
    private static final List<Waypoint> ITEMS=new ArrayList<Waypoint>();
    private static boolean loaded;
    private WaypointManager(){}

    public static synchronized List<Waypoint> all(){load();return Collections.unmodifiableList(new ArrayList<Waypoint>(ITEMS));}
    public static synchronized boolean add(String name,int x,int y,int z,int dimension){
        if(name==null||!name.matches("[A-Za-z0-9_-]{1,32}"))return false;
        load();removeInternal(name);removeWaypointProperties(name);Waypoint point=new Waypoint(name,x,y,z,dimension,0xFFFFD700);ITEMS.add(point);sort();writeWaypoint(VALUES,point);save();return true;
    }
    public static synchronized boolean remove(String name){load();boolean changed=removeInternal(name);if(changed){removeWaypointProperties(name);save();}return changed;}
    public static synchronized boolean exportProfile(String name){
        if(!validExportName(name))return false;load();Properties exported=new Properties();
        for(Waypoint point:ITEMS)writeWaypoint(exported,point);
        Path path=exportPath(name);return path!=null&&writeAtomic(path,exported,"Backspace waypoint export");
    }
    /** Merge a fully validated export into the current waypoint set; duplicate names are replaced. */
    public static synchronized int importProfile(String name){
        if(!validExportName(name))return -1;load();Path path=exportPath(name);if(path==null||!Files.isRegularFile(path))return -1;
        Properties incoming=new Properties();try(InputStream input=Files.newInputStream(path)){incoming.load(input);}catch(IOException failure){return -1;}
        Map<String,Waypoint> merged=new LinkedHashMap<String,Waypoint>();
        for(Waypoint point:ITEMS)merged.put(point.name.toLowerCase(Locale.ROOT),point);
        Map<String,Waypoint> parsed=new LinkedHashMap<String,Waypoint>();
        for(String property:incoming.stringPropertyNames())if(property.startsWith("waypoint.")&&property.endsWith(".x")){
            String pointName=property.substring(9,property.length()-2);
            if(!pointName.matches("[A-Za-z0-9_-]{1,32}"))return -1;
            try{
                String prefix=key(pointName)+".";
                Waypoint point=new Waypoint(pointName,Integer.parseInt(incoming.getProperty(prefix+"x")),Integer.parseInt(incoming.getProperty(prefix+"y")),Integer.parseInt(incoming.getProperty(prefix+"z")),Integer.parseInt(incoming.getProperty(prefix+"dimension")),Integer.parseInt(incoming.getProperty(prefix+"color","16766720")));
                parsed.put(pointName.toLowerCase(Locale.ROOT),point);
            }catch(RuntimeException malformed){return -1;}
        }
        if(parsed.isEmpty()||parsed.size()>4096)return -1;
        merged.putAll(parsed);if(merged.size()>4096)return -1;
        Properties replacement=new Properties();for(Waypoint point:merged.values())writeWaypoint(replacement,point);
        if(!writeAtomic(file(),replacement,"Backspace waypoints"))return -1;
        VALUES.clear();VALUES.putAll(replacement);ITEMS.clear();ITEMS.addAll(merged.values());sort();return parsed.size();
    }
    private static void load(){
        if(loaded)return;loaded=true;
        Path file=file();if(file==null)return;
        if(Files.isRegularFile(file))try(InputStream input=Files.newInputStream(file)){VALUES.load(input);}catch(IOException ignored){return;}
        for(String key:VALUES.stringPropertyNames())if(key.startsWith("waypoint.")&&key.endsWith(".x")){
            String name=key.substring(9,key.length()-2);if(!name.matches("[A-Za-z0-9_-]{1,32}"))continue;
            try{ITEMS.add(new Waypoint(name,Integer.parseInt(VALUES.getProperty(key)),Integer.parseInt(VALUES.getProperty("waypoint."+name+".y")),Integer.parseInt(VALUES.getProperty("waypoint."+name+".z")),Integer.parseInt(VALUES.getProperty("waypoint."+name+".dimension")),Integer.parseInt(VALUES.getProperty("waypoint."+name+".color","16766720"))));}catch(RuntimeException ignored){}
        }
        sort();
    }
    private static String key(String name){return "waypoint."+name;}
    private static void removeWaypointProperties(String name){List<String> remove=new ArrayList<String>();for(String property:VALUES.stringPropertyNames())if(property.startsWith("waypoint.")){int end=property.lastIndexOf('.');if(end>9&&property.substring(9,end).equalsIgnoreCase(name))remove.add(property);}for(String property:remove)VALUES.remove(property);}
    private static boolean removeInternal(String name){for(int i=0;i<ITEMS.size();i++)if(ITEMS.get(i).name.equalsIgnoreCase(name)){ITEMS.remove(i);return true;}return false;}
    private static void sort(){Collections.sort(ITEMS,new Comparator<Waypoint>(){@Override public int compare(Waypoint a,Waypoint b){return a.name.compareToIgnoreCase(b.name);}});}
    private static Path file(){return Paths.get(Minecraft.getMinecraft().mcDataDir.getAbsolutePath(),"Backspace","waypoints.properties");}
    private static Path exportPath(String name){if(!validExportName(name))return null;return file().getParent().resolve("waypoint-exports").resolve(name+".properties");}
    private static boolean validExportName(String name){return name!=null&&name.matches("[A-Za-z0-9_-]{1,32}");}
    private static void writeWaypoint(Properties properties,Waypoint point){String key=key(point.name);properties.setProperty(key+".x",Integer.toString(point.x));properties.setProperty(key+".y",Integer.toString(point.y));properties.setProperty(key+".z",Integer.toString(point.z));properties.setProperty(key+".dimension",Integer.toString(point.dimension));properties.setProperty(key+".color",Integer.toString(point.color));}
    private static boolean writeAtomic(Path target,Properties properties,String comment){Path temp=target.resolveSibling(target.getFileName().toString()+".tmp");try{Files.createDirectories(target.getParent());try(OutputStream output=Files.newOutputStream(temp)){properties.store(output,comment);}try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}return true;}catch(IOException failure){try{Files.deleteIfExists(temp);}catch(IOException ignored){}return false;}}
    private static void save(){
        Path file=file(),temp=file.resolveSibling(file.getFileName().toString()+".tmp");
        try{Files.createDirectories(file.getParent());try(OutputStream output=Files.newOutputStream(temp)){VALUES.store(output,"Backspace waypoints");}try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
        catch(IOException failure){try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }
}
