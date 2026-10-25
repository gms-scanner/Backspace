package dev.goldforge.module.impl;

import dev.goldforge.discovery.ChunkScanService;

/** Nether-only constructed route detector. */
public final class HighwayAnalyzer extends DiscoveryScanModule {
    public int minLine=12;
    public HighwayAnalyzer(){super("HighwayAnalyzer","Finds candidate Nether highways and crossings.","HIGHWAY");
        addIntSetting("Minimum line evidence",8,16,1,()->minLine,v->minLine=v);}
    @Override protected Finding find(ChunkScanService.Snapshot s){
        if(s.dimension!=-1)return null;
        int east=s.roadEastWest,north=s.roadNorthSouth;
        if(Math.max(east,north)<minLine)return null;
        boolean crossing=Math.min(east,north)>=minLine;
        String kind=crossing?"possible intersection":east>north?"east-west candidate":"north-south candidate";
        int confidence=Math.min(90,20+Math.max(east,north)*3+(crossing?15:0));
        return new Finding(s.chunkX*16+8,64,s.chunkZ*16+8,confidence,kind+"; constructed line evidence "+east+"/"+north,counts(s));
    }
}
