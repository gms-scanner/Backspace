package dev.goldforge.module.impl;

import dev.goldforge.discovery.ChunkScanService;

/** Persists observed container clusters, never inferred inventory contents. */
public final class StashTracker extends DiscoveryScanModule {
    public int minContainers=3;
    public StashTracker(){super("StashTracker","Records observed storage clusters by server and dimension.","STASH");
        addIntSetting("Minimum containers",1,24,1,()->minContainers,v->minContainers=v);}
    @Override protected Finding find(ChunkScanService.Snapshot s){
        int n=Math.max(s.storage.size(),s.count("minecraft:chest")+s.count("minecraft:trapped_chest")
                +s.count("minecraft:ender_chest")+s.count("minecraft:hopper"));if(n<minContainers)return null;
        int confidence=Math.min(100,20+n*9);
        return new Finding(s.chunkX*16+8,64,s.chunkZ*16+8,confidence,n+" observed container blocks (contents unknown)",counts(s));
    }
}
