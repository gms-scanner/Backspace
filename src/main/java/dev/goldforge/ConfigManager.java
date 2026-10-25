package dev.goldforge;

import dev.goldforge.gui.BrandMark;
import dev.goldforge.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Persistent client configuration stored independently of a world or server. */
public final class ConfigManager {
    private static final Properties VALUES=new Properties();
    private static Path file;
    private static boolean loaded,restored,restoring;
    private static String activeProfileName;
    public ConfigManager(){}

    public static synchronized void load(){
        if(loaded)return;
        file=Paths.get(Minecraft.getMinecraft().mcDataDir.getAbsolutePath(),"Backspace","config.properties");
        if(Files.exists(file))try(InputStream input=Files.newInputStream(file)){VALUES.load(input);}catch(IOException ignored){}
        for(Module module:GoldForge.MODULES.all()){
            module.key=getInt("module."+module.name+".key",module.key);
            for(Field field:module.getClass().getFields()){
                if(Modifier.isStatic(field.getModifiers())||Modifier.isFinal(field.getModifiers()))continue;
                String key="module."+module.name+"."+field.getName();
                try{
                    if(field.getType()==int.class&&VALUES.containsKey(key))field.setInt(module,parseInt(key,field.getInt(module)));
                    else if(field.getType()==float.class&&VALUES.containsKey(key))field.setFloat(module,parseFloat(key,field.getFloat(module)));
                    else if(field.getType()==double.class&&VALUES.containsKey(key))field.setDouble(module,parseDouble(key,field.getDouble(module)));
                    else if(field.getType()==boolean.class&&VALUES.containsKey(key))field.setBoolean(module,Boolean.parseBoolean(VALUES.getProperty(key)));
                    else if(field.getType()==String.class&&VALUES.containsKey(key))field.set(module,VALUES.getProperty(key,""));
                }catch(IllegalAccessException ignored){}
            }
        }
        dev.goldforge.gui.GuiPreferences.load(VALUES);
        BrandMark.hudX=getInt("hud.brand.x",BrandMark.hudX);BrandMark.hudY=getInt("hud.brand.y",BrandMark.hudY);
        BrandMark.hudRight=getBoolean("hud.brand.right",BrandMark.hudRight);BrandMark.hudBottom=getBoolean("hud.brand.bottom",BrandMark.hudBottom);
        dev.goldforge.network.PacketManager.setLogging(getBoolean("packetlog.enabled",false));
        loaded=true;
    }

    /** Apply stored enabled states once the client has a world; avoid calling module hooks during startup. */
    public static synchronized void restoreEnabled(){
        if(restored)return;load();
        restoring=true;
        try{for(Module module:GoldForge.MODULES.all()){
            boolean enabled=getBoolean("module."+module.name+".enabled",false);
            if(module.enabled!=enabled)module.setEnabled(enabled);
        }}finally{restoring=false;restored=true;}
        save();
    }

    public static synchronized void save(){
        if(!loaded||file==null||restoring)return;
        for(Module module:GoldForge.MODULES.all()){
            VALUES.setProperty("module."+module.name+".enabled",Boolean.toString(module.enabled));
            VALUES.setProperty("module."+module.name+".key",Integer.toString(module.key));
            for(Field field:module.getClass().getFields()){
                if(Modifier.isStatic(field.getModifiers())||Modifier.isFinal(field.getModifiers()))continue;
                try{
                    String key="module."+module.name+"."+field.getName();
                    if(field.getType()==int.class)VALUES.setProperty(key,Integer.toString(field.getInt(module)));
                    else if(field.getType()==float.class)VALUES.setProperty(key,Float.toString(field.getFloat(module)));
                    else if(field.getType()==double.class)VALUES.setProperty(key,Double.toString(field.getDouble(module)));
                    else if(field.getType()==boolean.class)VALUES.setProperty(key,Boolean.toString(field.getBoolean(module)));
                    else if(field.getType()==String.class)VALUES.setProperty(key,(String)field.get(module));
                }catch(IllegalAccessException ignored){}
            }
        }
        dev.goldforge.gui.GuiPreferences.save(VALUES);
        VALUES.setProperty("hud.brand.x",Integer.toString(BrandMark.hudX));VALUES.setProperty("hud.brand.y",Integer.toString(BrandMark.hudY));
        VALUES.setProperty("hud.brand.right",Boolean.toString(BrandMark.hudRight));VALUES.setProperty("hud.brand.bottom",Boolean.toString(BrandMark.hudBottom));
        VALUES.setProperty("packetlog.enabled",Boolean.toString(dev.goldforge.network.PacketManager.isLogging()));
        Path temporary=file.resolveSibling(file.getFileName().toString()+".tmp");
        try{
            Files.createDirectories(file.getParent());
            try(OutputStream output=Files.newOutputStream(temporary)){VALUES.store(output,"Backspace client settings");}
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException failure){try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
        if(activeProfileName!=null)copyConfigToProfile(activeProfileName);
    }

    @SubscribeEvent public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event){String name=serverProfileName();if(name!=null&&profilePath(name)!=null&&Files.isRegularFile(profilePath(name)))loadProfile(name);}
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event){save();activeProfileName=null;}
    public static synchronized boolean isRestoring(){return restoring;}
    public static synchronized boolean saveProfile(String name){
        Path profile=profilePath(name);if(profile==null)return false;
        save();boolean success=copyConfigToProfile(name);if(success)activeProfileName=name;return success;
    }
    public static synchronized boolean loadProfile(String name){
        Path profile=profilePath(name);if(profile==null||!Files.isRegularFile(profile))return false;
        try{
            Files.createDirectories(file.getParent());Path temporary=file.resolveSibling(file.getFileName().toString()+".profile.tmp");
            Files.copy(profile,temporary,StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            VALUES.clear();loaded=false;restored=false;load();
            activeProfileName=name;
            if(Minecraft.getMinecraft().player!=null&&Minecraft.getMinecraft().world!=null)restoreEnabled();
            return true;
        }catch(IOException ignored){return false;}
    }
    public static synchronized List<String> profiles(){
        List<String> names=new ArrayList<String>();if(file==null)return names;Path directory=file.getParent().resolve("profiles");
        if(Files.isDirectory(directory))try(DirectoryStream<Path> stream=Files.newDirectoryStream(directory,"*.properties")){for(Path path:stream){String name=path.getFileName().toString();names.add(name.substring(0,name.length()-11));}}catch(IOException ignored){}
        Collections.sort(names,String.CASE_INSENSITIVE_ORDER);return names;
    }
    public static synchronized boolean reload(){
        VALUES.clear();loaded=false;restored=false;load();
        if(Minecraft.getMinecraft().player!=null&&Minecraft.getMinecraft().world!=null)restoreEnabled();
        return true;
    }
    private static Path profilePath(String name){
        if(name==null||!name.matches("[A-Za-z0-9_-]{1,32}")||file==null)return null;
        return file.getParent().resolve("profiles").resolve(name+".properties");
    }
    private static boolean copyConfigToProfile(String name){Path profile=profilePath(name);if(profile==null||file==null)return false;Path temp=profile.resolveSibling(profile.getFileName().toString()+".tmp");try{Files.createDirectories(profile.getParent());Files.copy(file,temp,StandardCopyOption.REPLACE_EXISTING);try{Files.move(temp,profile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,profile,StandardCopyOption.REPLACE_EXISTING);}return true;}catch(IOException failure){try{Files.deleteIfExists(temp);}catch(IOException ignored){}return false;}}
    public static synchronized String currentServerProfileName(){return serverProfileName();}
    public static synchronized void applyServerProfileIfAvailable(){String name=serverProfileName();if(name!=null&&!name.equals(activeProfileName)&&profilePath(name)!=null&&Files.isRegularFile(profilePath(name)))loadProfile(name);}
    private static String serverProfileName(){net.minecraft.client.multiplayer.ServerData server=Minecraft.getMinecraft().getCurrentServerData();if(server==null||server.serverIP==null||server.serverIP.trim().isEmpty())return null;return "server-"+String.format(java.util.Locale.ROOT,"%08x",server.serverIP.trim().toLowerCase(java.util.Locale.ROOT).hashCode());}
    private static int getInt(String key,int fallback){return parseInt(key,fallback);}
    private static int parseInt(String key,int fallback){try{return Integer.parseInt(VALUES.getProperty(key));}catch(Exception ignored){return fallback;}}
    private static float parseFloat(String key,float fallback){try{return Float.parseFloat(VALUES.getProperty(key));}catch(Exception ignored){return fallback;}}
    private static double parseDouble(String key,double fallback){try{return Double.parseDouble(VALUES.getProperty(key));}catch(Exception ignored){return fallback;}}
    private static boolean getBoolean(String key,boolean fallback){String value=VALUES.getProperty(key);return value==null?fallback:Boolean.parseBoolean(value);}
}

