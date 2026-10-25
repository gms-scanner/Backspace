package dev.goldforge.module.impl;

import dev.goldforge.WaypointManager;
import dev.goldforge.discovery.DiscoveryDatabase;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/** Direction and distance to the nearest saved waypoint in this dimension. */
public final class WaypointNavigator extends Module {
    public int x=8,y=124,maxDistance=100000,includeDiscoveries=1;
    private String label="";
    public WaypointNavigator(){super("WaypointNavigator","Shows direction and distance to the nearest waypoint.",Category.HUD,0);
        addIntSetting("X",0,800,4,()->x,v->x=v);addIntSetting("Y",0,600,4,()->y,v->y=v);
        addIntSetting("Maximum distance",100,100000,100,()->maxDistance,v->maxDistance=v);
        addIntSetting("Include discoveries",0,1,1,()->includeDiscoveries,v->includeDiscoveries=v);}
    @Override public void tick(){
        if(mc.player==null||mc.world==null){label="";return;}
        double best=maxDistance*(double)maxDistance;String name="";int tx=0,tz=0;
        for(WaypointManager.Waypoint p:WaypointManager.all()){
            if(p.dimension!=mc.player.dimension)continue;
            double d=distance(p.x,p.z);if(d<best){best=d;name=p.name;tx=p.x;tz=p.z;}
        }
        if(includeDiscoveries!=0)for(DiscoveryDatabase.Entry p:DiscoveryDatabase.all()){
            if(!p.waypoint||p.dimension!=mc.player.dimension)continue;
            double d=distance(p.x,p.z);if(d<best){best=d;name=p.name;tx=p.x;tz=p.z;}
        }
        if(name.isEmpty()){label="No waypoint in range";return;}
        double dx=tx-mc.player.posX,dz=tz-mc.player.posZ;
        double angle=Math.toDegrees(Math.atan2(-dx,dz));double relative=(angle-mc.player.rotationYaw+540D)%360D-180D;
        String arrow=Math.abs(relative)<22.5?"↑":relative>0?relative<112.5?"→":"↓":relative>-112.5?"←":"↓";
        label=name+" "+arrow+" "+Math.round(Math.sqrt(best))+"m";
    }
    private double distance(int x,int z){double dx=x-mc.player.posX,dz=z-mc.player.posZ;return dx*dx+dz*dz;}
    @Override public void onOverlay(RenderGameOverlayEvent.Post event){if(event.getType()==RenderGameOverlayEvent.ElementType.TEXT&&!label.isEmpty())mc.fontRenderer.drawStringWithShadow(label,x,y,0xFFFFD700);}
    @Override protected void onDisable(){label="";}
}
