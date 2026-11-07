package dev.goldforge.module.impl;

import dev.goldforge.discovery.ChunkScanService;

/** Treats linear constructed block patterns as candidate routes. */
public final class TrailFinder extends DiscoveryScanModule {
    public int minLine=12;
    public TrailFinder(){super("TrailFinder","Finds candidate roads, bridges and torch trails.","TRAIL");
        addIntSetting("Minimum line evidence",8,16,1,()->minLine,v->minLine=v);}
    @Override protected Finding find(ChunkScanService.Snapshot s){
        int strength=Math.max(s.roadEastWest,s.roadNorthSouth);if(strength<minLine)return null;
        String direction=s.roadEastWest>=s.roadNorthSouth?"east-west":"north-south";
        int confidence=Math.min(75,20+strength*3);
        return new Finding(s.chunkX*16+8,64,s.chunkZ*16+8,confidence,"candidate "+direction+" constructed route; line evidence "+strength,counts(s));
    }
}
