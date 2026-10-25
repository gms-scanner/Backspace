package dev.goldforge.module.impl;

import dev.goldforge.WaypointManager;
import dev.goldforge.discovery.DiscoveryDatabase;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Route supervisor. Baritone is invoked reflectively only when it is installed. */
public final class ExpeditionMode extends Module {
    public int minHealth=10,minHunger=7,minFreeSlots=1,x=8,y=202,autoBaritone=1;
    private final List<String> route=new ArrayList<String>();
    private int targetIndex,lastPathIndex=-1;private boolean active,paused;
    private String pauseReason="";private long started;private double traveled,lastX=Double.NaN,lastZ=Double.NaN;
    private int lastDimension=Integer.MIN_VALUE;
    public ExpeditionMode(){super("ExpeditionMode","Follows waypoint routes with survival pauses and optional Baritone.",Category.PLAYER,0);
        addIntSetting("Minimum health",2,20,1,()->minHealth,v->minHealth=v);
        addIntSetting("Minimum hunger",0,20,1,()->minHunger,v->minHunger=v);
        addIntSetting("Minimum free slots",0,9,1,()->minFreeSlots,v->minFreeSlots=v);
        addIntSetting("Use Baritone if installed",0,1,1,()->autoBaritone,v->autoBaritone=v);
        addIntSetting("HUD X",0,800,4,()->x,v->x=v);addIntSetting("HUD Y",0,600,4,()->y,v->y=v);}
    public String status(){return (active?paused?"Paused: "+pauseReason:"Running":"Stopped")+" | waypoint "+(targetIndex+1)+"/"+route.size()+" | "+Math.round(traveled)+"m";}
    public void route(List<String> names){route.clear();route.addAll(names);targetIndex=0;lastPathIndex=-1;active=false;paused=false;checkpoint();}
    public boolean start(){if(route.isEmpty()||mc.player==null)return false;active=true;paused=false;pauseReason="";started=System.currentTimeMillis();lastDimension=mc.player.dimension;setEnabled(true);checkpoint();return true;}
    public void stop(){active=false;paused=false;cancelBaritone();checkpoint();}
    public boolean resume(){if(route.isEmpty()||mc.player==null)return false;active=true;paused=false;pauseReason="";lastPathIndex=-1;lastDimension=mc.player.dimension;setEnabled(true);return true;}
    public boolean restore(){
        if(mc.world==null)return false;
        Path file=checkpointFile();if(!Files.isRegularFile(file))return false;
        Properties props=new Properties();try(InputStream in=Files.newInputStream(file)){props.load(in);}catch(IOException failure){return false;}
        if(!DiscoveryDatabase.currentServer().equals(props.getProperty("server")))return false;
        try{
            route.clear();int count=Math.min(64,Integer.parseInt(props.getProperty("count","0")));
            for(int i=0;i<count;i++){String name=props.getProperty("route."+i);if(name!=null)route.add(name);}
            targetIndex=Math.max(0,Math.min(route.size(),Integer.parseInt(props.getProperty("index","0"))));
            traveled=Double.parseDouble(props.getProperty("traveled","0"));
        }catch(RuntimeException invalid){route.clear();return false;}
        active=false;paused=true;pauseReason="Reconnect: run /expedition resume";return !route.isEmpty();
    }
    @Override public void tick(){
        if(!active||mc.player==null||mc.world==null)return;
        if(lastDimension!=mc.player.dimension){pause("Dimension changed");return;}
        if(!Double.isNaN(lastX)){double dx=mc.player.posX-lastX,dz=mc.player.posZ-lastZ;if(dx*dx+dz*dz<256)traveled+=Math.sqrt(dx*dx+dz*dz);}
        lastX=mc.player.posX;lastZ=mc.player.posZ;
        if(mc.player.getHealth()+mc.player.getAbsorptionAmount()<=minHealth){pause("Low health");return;}
        if(mc.player.getFoodStats().getFoodLevel()<=minHunger){pause("Low hunger");return;}
        int empty=0;for(int slot=0;slot<36;slot++)if(mc.player.inventory.getStackInSlot(slot).isEmpty())empty++;
        if(empty<minFreeSlots){pause("Inventory full");return;}
        if(mc.player.isBurning()||mc.player.isInLava()||mc.player.getAir()<=40){pause("Environment danger");return;}
        if(targetIndex>=route.size()){stop();mc.player.sendMessage(new TextComponentString("[Backspace] Expedition complete."));return;}
        WaypointManager.Waypoint target=target();
        if(target==null){pause("Waypoint missing or different dimension: "+route.get(targetIndex));return;}
        double dx=target.x-mc.player.posX,dz=target.z-mc.player.posZ;
        if(dx*dx+dz*dz<16D){targetIndex++;lastPathIndex=-1;checkpoint();return;}
        if(autoBaritone!=0&&lastPathIndex!=targetIndex&&pathTo(target)){lastPathIndex=targetIndex;}
    }
    private WaypointManager.Waypoint target(){for(WaypointManager.Waypoint waypoint:WaypointManager.all())if(waypoint.name.equalsIgnoreCase(route.get(targetIndex))&&waypoint.dimension==mc.player.dimension)return waypoint;return null;}
    private void pause(String reason){active=false;paused=true;pauseReason=reason;cancelBaritone();checkpoint();if(mc.player!=null)mc.player.sendMessage(new TextComponentString("[Backspace] Expedition paused: "+reason));}
    private boolean pathTo(WaypointManager.Waypoint target){
        try{
            Class<?> api=Class.forName("baritone.api.BaritoneAPI");Object provider=api.getMethod("getProvider").invoke(null);
            Object baritone=provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object process=baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            Class<?> goal=Class.forName("baritone.api.pathing.goals.Goal");Class<?> block=Class.forName("baritone.api.pathing.goals.GoalBlock");
            Object location=block.getConstructor(int.class,int.class,int.class).newInstance(target.x,target.y,target.z);
            process.getClass().getMethod("setGoalAndPath",goal).invoke(process,location);return true;
        }catch(ReflectiveOperationException|LinkageError unavailable){return false;}
    }
    private void cancelBaritone(){
        if(lastPathIndex<0)return;
        try{Class<?> api=Class.forName("baritone.api.BaritoneAPI");Object provider=api.getMethod("getProvider").invoke(null);
            Object baritone=provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object process=baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            process.getClass().getMethod("cancelEverything").invoke(process);
        }catch(ReflectiveOperationException|LinkageError ignored){}
        lastPathIndex=-1;
    }
    private Path checkpointFile(){return mc.mcDataDir.toPath().resolve("Backspace").resolve("expedition-checkpoint.properties");}
    private void checkpoint(){
        if(mc.world==null)return;Properties props=new Properties();props.setProperty("server",DiscoveryDatabase.currentServer());
        props.setProperty("dimension",Integer.toString(mc.world.provider.getDimension()));props.setProperty("count",Integer.toString(route.size()));
        props.setProperty("index",Integer.toString(targetIndex));props.setProperty("traveled",Double.toString(traveled));
        for(int i=0;i<route.size();i++)props.setProperty("route."+i,route.get(i));
        Path file=checkpointFile();try{Files.createDirectories(file.getParent());try(OutputStream out=Files.newOutputStream(file)){props.store(out,"Backspace expedition checkpoint");}}catch(IOException ignored){}
    }
    @Override public void onOverlay(RenderGameOverlayEvent.Post event){
        if(event.getType()!=RenderGameOverlayEvent.ElementType.TEXT||mc.player==null||route.isEmpty())return;
        String text="EXPEDITION "+status();int width=mc.fontRenderer.getStringWidth(text);
        Gui.drawRect(x-4,y-2,x+width+5,y+mc.fontRenderer.FONT_HEIGHT+2,0xB80A0A0A);
        mc.fontRenderer.drawStringWithShadow(text,x,y,paused?0xFFFF7777:0xFFFFD700);
    }
    @Override protected void onWorldUnload(){if(active)pause("Disconnected; confirm resume after reconnect");checkpoint();lastX=Double.NaN;lastZ=Double.NaN;}
    @Override protected void onDisable(){if(active)stop();}
}
