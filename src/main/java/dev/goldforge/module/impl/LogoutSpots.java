package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Last observed positions of players removed from the server player list. */
public final class LogoutSpots extends Module {
    public int range=128, minutes=10, maxSpots=32, labels=1;
    private final Map<UUID,Snapshot> seen=new HashMap<UUID,Snapshot>();
    private final Map<UUID,Snapshot> spots=new LinkedHashMap<UUID,Snapshot>();
    private int tick;
    public LogoutSpots(){
        super("LogoutSpots","Marks the last observed positions of disconnected players.",Category.RENDER,0);
        addIntSetting("Render range",16,256,16,()->range,v->range=v);
        addIntSetting("Keep (minutes)",1,60,1,()->minutes,v->minutes=v);
        addIntSetting("Maximum spots",4,64,4,()->maxSpots,v->maxSpots=v);
        addIntSetting("Labels (0/1)",0,1,1,()->labels,v->labels=v);
    }
    @Override protected void onEnable(){seen.clear();spots.clear();tick=0;}
    @Override protected void onDisable(){seen.clear();spots.clear();}
    @Override protected void onWorldUnload(){seen.clear();spots.clear();}
    @Override public void tick(){
        if(mc.world==null||mc.player==null)return;
        tick++;
        for(EntityPlayer player:mc.world.playerEntities){
            if(player==mc.player)continue;
            Snapshot previous=seen.get(player.getUniqueID());
            if(previous!=null&&previous.tick==tick)continue;
            seen.put(player.getUniqueID(),new Snapshot(player.getName(),player.posX,player.posY,player.posZ,player.dimension,tick));
            spots.remove(player.getUniqueID());
        }
        Iterator<Map.Entry<UUID,Snapshot>> it=spots.entrySet().iterator();
        while(it.hasNext())if(tick-it.next().getValue().tick>minutes*1200)it.remove();
        seen.entrySet().removeIf(entry->tick-entry.getValue().tick>200);
    }
    public void onPlayerListChange(UUID id,boolean removed){
        if(!enabled||id==null)return;
        if(!removed){spots.remove(id);return;}
        if(mc.player!=null&&id.equals(mc.player.getUniqueID()))return;
        Snapshot snapshot=seen.get(id);
        if(snapshot==null||tick-snapshot.tick>40)return;
        spots.put(id,new Snapshot(snapshot.name,snapshot.x,snapshot.y,snapshot.z,snapshot.dimension,tick));
        while(spots.size()>maxSpots)spots.remove(spots.keySet().iterator().next());
    }
    @Override public void render(float partialTicks){
        if(mc.player==null||mc.getRenderManager()==null)return;
        CombatVisuals.begin();
        try{
            for(Snapshot spot:spots.values()){
                if(spot.dimension!=mc.player.dimension)continue;
                double dx=spot.x-mc.player.posX,dy=spot.y-mc.player.posY,dz=spot.z-mc.player.posZ;
                if(dx*dx+dy*dy+dz*dz>range*(double)range)continue;
                AxisAlignedBB body=new AxisAlignedBB(spot.x-.3,spot.y,spot.z-.3,spot.x+.3,spot.y+1.8,spot.z+.3);
                CombatVisuals.fill(body,1F,.72F,.05F,.08F);
                CombatVisuals.outline(body,1F,.75F,.09F,.9F);
                if(labels!=0)CombatVisuals.label(spot.name+" · "+(int)Math.floor(spot.x)+", "+(int)Math.floor(spot.y)+", "+(int)Math.floor(spot.z),spot.x,spot.y+2.08,spot.z,0xFFFFDA62);
            }
        }finally{CombatVisuals.end();}
    }
    private static final class Snapshot{
        final String name;final double x,y,z;final int dimension,tick;
        Snapshot(String name,double x,double y,double z,int dimension,int tick){this.name=name;this.x=x;this.y=y;this.z=z;this.dimension=dimension;this.tick=tick;}
    }
}
