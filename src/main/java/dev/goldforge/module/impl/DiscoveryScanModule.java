package dev.goldforge.module.impl;

import dev.goldforge.discovery.ChunkScanService;
import dev.goldforge.discovery.DiscoveryDatabase;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import java.util.LinkedHashMap;
import java.util.Map;

abstract class DiscoveryScanModule extends Module implements ChunkScanService.Listener {
    public int range=256,minConfidence=50,autoWaypoint=1,notify=1;
    private final String type;
    DiscoveryScanModule(String name,String description,String type){
        super(name,description,Category.RENDER,0);this.type=type;
        addIntSetting("Detection range",32,512,16,()->range,v->range=v);
        addIntSetting("Minimum confidence",0,100,5,()->minConfidence,v->minConfidence=v);
        addIntSetting("Auto waypoint",0,1,1,()->autoWaypoint,v->autoWaypoint=v);
        addIntSetting("Notify",0,1,1,()->notify,v->notify=v);
    }
    protected static final class Finding {
        final int x,y,z,confidence;final String reason;final Map<String,Integer> counts;
        Finding(int x,int y,int z,int confidence,String reason,Map<String,Integer> counts){
            this.x=x;this.y=y;this.z=z;this.confidence=confidence;this.reason=reason;this.counts=counts;
        }
    }
    protected abstract Finding find(ChunkScanService.Snapshot snapshot);
    @Override public final void scanned(ChunkScanService.Snapshot snapshot){
        if(!enabled||isSuspended()||mc.player==null||mc.world==null)return;
        double dx=snapshot.chunkX*16+8-mc.player.posX,dz=snapshot.chunkZ*16+8-mc.player.posZ;
        if(dx*dx+dz*dz>range*(double)range)return;
        Finding finding=find(snapshot);if(finding==null||finding.confidence<minConfidence)return;
        boolean prior=false;
        for(DiscoveryDatabase.Entry entry:DiscoveryDatabase.all())if(type.equals(entry.type)
                &&Math.abs(entry.x-finding.x)<=32&&Math.abs(entry.z-finding.z)<=32){prior=true;break;}
        DiscoveryDatabase.observe(type,finding.x,finding.y,finding.z,finding.confidence,finding.reason,finding.counts,32,autoWaypoint!=0);
        if(!prior&&notify!=0)DiscoveryNotifier.alert(name,finding.confidence,finding.x,finding.z,finding.reason);
    }
    static Map<String,Integer> counts(ChunkScanService.Snapshot snapshot){
        Map<String,Integer> result=new LinkedHashMap<String,Integer>(snapshot.tiles);
        for(Map.Entry<String,Integer> entry:snapshot.blocks.entrySet())if(entry.getValue()>0)result.put(entry.getKey(),entry.getValue());
        return result;
    }
}
