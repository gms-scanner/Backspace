package dev.goldforge.module.impl;

import dev.goldforge.GoldForge;
import dev.goldforge.discovery.ChunkScanService;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Compact live profiler using the existing hook timing windows. */
public final class PerformanceProfiler extends Module {
    public int x=8,y=180,topHooks=4,scanBudget=4096;
    private String[] lines=new String[0];private int ticks;
    public PerformanceProfiler(){super("PerformanceProfiler","Shows module costs and bounds background chunk scanning.",Category.HUD,0);
        addIntSetting("X",0,800,4,()->x,v->x=v);addIntSetting("Y",0,600,4,()->y,v->y=v);
        addIntSetting("Top hooks",1,8,1,()->topHooks,v->topHooks=v);
        addIntSetting("Scan blocks per tick",256,16384,256,()->scanBudget,v->scanBudget=v);}
    @Override public void tick(){
        ChunkScanService scanner=GoldForge.MODULES.chunkScanner();scanner.blockBudget=scanBudget;
        if(++ticks%20!=0)return;
        List<Map.Entry<String,Long>> timings=new ArrayList<Map.Entry<String,Long>>(GoldForge.MODULES.averageTimingsNanos().entrySet());
        Collections.sort(timings,(a,b)->Long.compare(b.getValue(),a.getValue()));
        List<String> display=new ArrayList<String>();
        display.add("SCAN "+scanner.pending()+" pending | "+scanner.scannedChunks()+" chunks | "+(scanner.lastNanos()/1000L)+" µs");
        for(int i=0;i<Math.min(topHooks,timings.size());i++)display.add(timings.get(i).getKey()+" "+(timings.get(i).getValue()/1000L)+" µs");
        lines=display.toArray(new String[display.size()]);
    }
    @Override public void onOverlay(RenderGameOverlayEvent.Post event){if(event.getType()!=RenderGameOverlayEvent.ElementType.TEXT)return;int yy=y;for(String line:lines){mc.fontRenderer.drawStringWithShadow(line,x,yy,0xFFFFD700);yy+=mc.fontRenderer.FONT_HEIGHT+2;}}
    @Override protected void onDisable(){lines=new String[0];}
}
