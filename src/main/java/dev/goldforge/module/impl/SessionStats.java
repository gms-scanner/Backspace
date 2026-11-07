package dev.goldforge.module.impl;

import dev.goldforge.discovery.DiscoveryDatabase;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import java.util.HashSet;
import java.util.Set;

/** Current-session travel and exploration counters. */
public final class SessionStats extends Module {
    public int x=8,y=150;
    private long started;private double distance,lastX,lastZ;private int discoveries,ticks;private final Set<Long> chunks=new HashSet<Long>();
    public SessionStats(){super("SessionStats","Shows travel, visited chunks and discoveries this session.",Category.HUD,0);
        addIntSetting("X",0,800,4,()->x,v->x=v);addIntSetting("Y",0,600,4,()->y,v->y=v);}
    @Override protected void onEnable(){reset();}
    @Override protected void onWorldUnload(){lastX=Double.NaN;lastZ=Double.NaN;}
    @Override public void tick(){
        if(mc.player==null)return;
        if(!Double.isNaN(lastX)){
            double dx=mc.player.posX-lastX,dz=mc.player.posZ-lastZ;
            if(dx*dx+dz*dz<256)distance+=Math.sqrt(dx*dx+dz*dz);
        }
        lastX=mc.player.posX;lastZ=mc.player.posZ;
        chunks.add(((long)mc.player.chunkCoordX<<32)^(mc.player.chunkCoordZ&0xffffffffL));
        if(++ticks%20==0)discoveries=DiscoveryDatabase.all().size();
    }
    @Override public void onOverlay(RenderGameOverlayEvent.Post event){
        if(event.getType()!=RenderGameOverlayEvent.ElementType.TEXT||mc.player==null)return;
        long seconds=(System.currentTimeMillis()-started)/1000L;
        mc.fontRenderer.drawStringWithShadow("SESSION "+(seconds/60)+"m | "+Math.round(distance)+"m | "+chunks.size()+" chunks | "+discoveries+" discoveries",x,y,0xFFFFD700);
    }
    @Override protected void onDisable(){reset();}
    private void reset(){started=System.currentTimeMillis();distance=0;lastX=Double.NaN;lastZ=Double.NaN;chunks.clear();discoveries=0;ticks=0;}
}
