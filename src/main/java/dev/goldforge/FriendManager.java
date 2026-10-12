package dev.goldforge;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Shared local friend, group, whitelist, and blacklist data. */
public final class FriendManager {
    private static final Map<String,String> FRIENDS=new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);
    private static final Set<String> WHITELIST=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    private static final Set<String> BLACKLIST=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    private static Path file;
    private static boolean loaded;
    private FriendManager(){}

    public static synchronized void load(){
        file=Paths.get(Minecraft.getMinecraft().mcDataDir.getAbsolutePath(),"Backspace","friends.properties");
        FRIENDS.clear();WHITELIST.clear();BLACKLIST.clear();Properties values=new Properties();
        if(Files.isRegularFile(file))try(InputStream input=Files.newInputStream(file)){values.load(input);}catch(IOException ignored){}
        for(String property:values.stringPropertyNames()){
            if(property.startsWith("friend.")){String name=property.substring(7);if(valid(name))FRIENDS.put(name,validGroup(values.getProperty(property))?values.getProperty(property):"Friends");}
            else if(property.startsWith("whitelist.")){String name=property.substring(10);if(valid(name)&&Boolean.parseBoolean(values.getProperty(property)))WHITELIST.add(name);}
            else if(property.startsWith("blacklist.")){String name=property.substring(10);if(valid(name)&&Boolean.parseBoolean(values.getProperty(property)))BLACKLIST.add(name);}
        }
        // Import the previous plain-text list when upgrading an existing install.
        Path legacy=file.resolveSibling("friends.txt");
        if(FRIENDS.isEmpty()&&!Files.exists(file)&&Files.isRegularFile(legacy))try{for(String line:Files.readAllLines(legacy,StandardCharsets.UTF_8)){String name=line.trim();if(valid(name))FRIENDS.put(name,"Friends");}if(!FRIENDS.isEmpty())save();}catch(IOException ignored){}
        loaded=true;
    }
    public static synchronized boolean add(String name){if(!valid(name))return false;ensureLoaded();if(FRIENDS.containsKey(name))return false;FRIENDS.put(name,"Friends");save();return true;}
    public static synchronized boolean remove(String name){ensureLoaded();if(!valid(name))return false;boolean changed=FRIENDS.remove(name)!=null;changed|=WHITELIST.remove(name);changed|=BLACKLIST.remove(name);if(changed)save();return changed;}
    public static synchronized boolean rename(String oldName,String newName){ensureLoaded();if(!valid(oldName)||!valid(newName)||!FRIENDS.containsKey(oldName)||FRIENDS.containsKey(newName))return false;String group=FRIENDS.remove(oldName);FRIENDS.put(newName,group);replace(WHITELIST,oldName,newName);replace(BLACKLIST,oldName,newName);save();return true;}
    public static synchronized boolean setGroup(String name,String group){ensureLoaded();if(!valid(name)||!validGroup(group)||!FRIENDS.containsKey(name))return false;FRIENDS.put(name,group);save();return true;}
    public static synchronized String group(String name){ensureLoaded();String group=FRIENDS.get(name);return group==null?"":group;}
    public static synchronized boolean isFriend(String name){ensureLoaded();return name!=null&&FRIENDS.containsKey(name);}
    public static synchronized boolean addWhitelist(String name){ensureLoaded();if(!valid(name)||!WHITELIST.add(name))return false;save();return true;}
    public static synchronized boolean removeWhitelist(String name){ensureLoaded();if(!WHITELIST.remove(name))return false;save();return true;}
    public static synchronized boolean isWhitelisted(String name){ensureLoaded();return name!=null&&WHITELIST.contains(name);}
    public static synchronized Set<String> whitelist(){ensureLoaded();return Collections.unmodifiableSet(new TreeSet<String>(WHITELIST));}
    public static synchronized boolean addBlacklist(String name){ensureLoaded();if(!valid(name)||!BLACKLIST.add(name))return false;save();return true;}
    public static synchronized boolean removeBlacklist(String name){ensureLoaded();if(!BLACKLIST.remove(name))return false;save();return true;}
    public static synchronized boolean isBlacklisted(String name){ensureLoaded();return name!=null&&BLACKLIST.contains(name);}
    public static synchronized Set<String> blacklist(){ensureLoaded();return Collections.unmodifiableSet(new TreeSet<String>(BLACKLIST));}
    public static synchronized Set<String> all(){ensureLoaded();return Collections.unmodifiableSet(new TreeSet<String>(FRIENDS.keySet()));}
    public static synchronized Map<String,String> grouped(){ensureLoaded();return Collections.unmodifiableMap(new TreeMap<String,String>(FRIENDS));}

    private static void replace(Set<String> values,String oldName,String newName){if(values.remove(oldName))values.add(newName);}
    private static boolean valid(String name){return name!=null&&name.matches("[A-Za-z0-9_]{1,16}");}
    private static boolean validGroup(String group){return group!=null&&group.matches("[A-Za-z0-9_-]{1,24}");}
    private static void ensureLoaded(){if(!loaded)load();}
    private static void save(){
        Properties values=new Properties();for(Map.Entry<String,String> entry:FRIENDS.entrySet())values.setProperty("friend."+entry.getKey(),entry.getValue());for(String name:WHITELIST)values.setProperty("whitelist."+name,"true");for(String name:BLACKLIST)values.setProperty("blacklist."+name,"true");
        Path temp=file.resolveSibling(file.getFileName().toString()+".tmp");
        try{Files.createDirectories(file.getParent());try(OutputStream output=Files.newOutputStream(temp)){values.store(output,"Backspace friend lists");}try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
        catch(IOException failure){try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }
}
