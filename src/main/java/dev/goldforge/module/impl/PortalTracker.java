package dev.goldforge.module.impl;

import dev.goldforge.discovery.ChunkScanService;
import net.minecraft.util.math.BlockPos;

public final class PortalTracker extends DiscoveryScanModule {
    public PortalTracker(){super("PortalTracker","Records portals observed in loaded chunks.","PORTAL");minConfidence=0;}
    @Override protected Finding find(ChunkScanService.Snapshot s){
        if(s.portals.isEmpty())return null;
        BlockPos pos=s.portals.get(0);
        return new Finding(pos.getX(),pos.getY(),pos.getZ(),95,s.portals.size()+" observed portal blocks",counts(s));
    }
}
