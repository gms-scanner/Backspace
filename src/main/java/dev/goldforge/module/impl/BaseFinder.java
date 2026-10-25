package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.discovery.ChunkScanService;
import dev.goldforge.discovery.DiscoveryDatabase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.tileentity.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Flags loaded client chunks containing clusters of storage and utility tile entities. */
public final class BaseFinder extends Module implements ChunkScanService.Listener {
    public int range=256,scanIntervalTicks=10,evidenceThreshold=4,cacheSeconds=300,minConfidence=55,autoWaypoint=1,notify=1;
    private static final int MAX_CANDIDATES=256;
    private final LinkedHashMap<Long,Signal> candidates=new LinkedHashMap<Long,Signal>();
    private int ticks;

    public BaseFinder(){
        super("BaseFinder","Marks loaded chunks with storage/utility clusters as possible base signals.",Category.RENDER,0);
        addIntSetting("Scan range",64,512,32,()->range,v->range=v);
        addIntSetting("Scan interval ticks",1,40,1,()->scanIntervalTicks,v->scanIntervalTicks=v);
        addIntSetting("Evidence threshold",2,12,1,()->evidenceThreshold,v->evidenceThreshold=v);
        addIntSetting("Remember signal seconds",30,900,30,()->cacheSeconds,v->cacheSeconds=v);
        addIntSetting("Minimum confidence",0,100,5,()->minConfidence,v->minConfidence=v);
        addIntSetting("Auto waypoint",0,1,1,()->autoWaypoint,v->autoWaypoint=v);
        addIntSetting("Notify",0,1,1,()->notify,v->notify=v);
    }

    @Override protected void onEnable(){candidates.clear();ticks=0;}
    @Override protected void onDisable(){candidates.clear();}
    @Override protected void onWorldUnload(){candidates.clear();}

    @Override public void tick(){
        if(mc.player==null||mc.world==null){candidates.clear();return;}
        ticks++;
        long now=System.currentTimeMillis();
        long cutoff=now-cacheSeconds*1000L;
        Iterator<Map.Entry<Long,Signal>> iterator=candidates.entrySet().iterator();
        while(iterator.hasNext())if(iterator.next().getValue().lastSeen<cutoff)iterator.remove();
    }

    @Override public void scanned(ChunkScanService.Snapshot snapshot){
        if(!enabled||isSuspended()||mc.player==null||mc.world==null)return;
        int x=snapshot.chunkX*16+8,z=snapshot.chunkZ*16+8;
        double dx=x-mc.player.posX,dz=z-mc.player.posZ;if(dx*dx+dz*dz>range*(double)range)return;
        int storage=Math.max(snapshot.storage.size(),snapshot.count("minecraft:chest")+snapshot.count("minecraft:trapped_chest")
                +snapshot.count("minecraft:ender_chest")+snapshot.count("minecraft:hopper"));
        int utility=snapshot.tileCount("TileEntityFurnace")+snapshot.tileCount("TileEntityHopper")
                +snapshot.tileCount("TileEntityBrewingStand");
        int artificial=snapshot.count("minecraft:obsidian")+snapshot.count("minecraft:crafting_table")
                +snapshot.count("minecraft:anvil")+snapshot.count("minecraft:bed");
        int crops=snapshot.count("minecraft:farmland")+snapshot.count("minecraft:wheat")
                +snapshot.count("minecraft:carrots")+snapshot.count("minecraft:potatoes")
                +snapshot.count("minecraft:nether_wart");
        int score=storage*3+utility*2+Math.min(12,artificial/4)+Math.min(10,crops/20);
        if(score<evidenceThreshold)return;
        int confidence=Math.min(100,10+storage*10+utility*5+Math.min(20,artificial/3)+Math.min(20,crops/15));
        if(confidence<minConfidence)return;
        long key=chunkKey(snapshot.chunkX,snapshot.chunkZ);
        boolean fresh=true;
        for(DiscoveryDatabase.Entry known:DiscoveryDatabase.all())if("BASE".equals(known.type)
                &&Math.abs(known.x-x)<=32&&Math.abs(known.z-z)<=32){fresh=false;break;}
        List<BlockPos> examples=snapshot.storage.size()>3?snapshot.storage.subList(0,3):snapshot.storage;
        candidates.put(key,new Signal(snapshot.chunkX,snapshot.chunkZ,score,storage+utility,System.currentTimeMillis(),examples));
        Map<String,Integer> counts=new LinkedHashMap<String,Integer>(snapshot.tiles);
        String reason=storage+" observed storage/utility positions, "+artificial+" artificial blocks, "+crops+" crop/farm blocks";
        DiscoveryDatabase.observe("BASE",x,64,z,confidence,reason,counts,32,autoWaypoint!=0);
        if(fresh&&notify!=0)DiscoveryNotifier.alert("Possible base",confidence,x,z,reason);
        while(candidates.size()>MAX_CANDIDATES){Iterator<Long> oldest=candidates.keySet().iterator();if(!oldest.hasNext())break;oldest.next();oldest.remove();}
    }

    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null||mc.getRenderManager()==null||candidates.isEmpty())return;
        double vx=mc.getRenderManager().viewerPosX,vy=mc.getRenderManager().viewerPosY,vz=mc.getRenderManager().viewerPosZ;
        int rangeChunks=(range+15)/16;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LINE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.disableDepth();GlStateManager.depthMask(false);GL11.glLineWidth(2F);
            for(Signal signal:candidates.values()){
                if(Math.abs(signal.chunkX-mc.player.chunkCoordX)>rangeChunks||Math.abs(signal.chunkZ-mc.player.chunkCoordZ)>rangeChunks)continue;
                double x=signal.chunkX*16D-vx,z=signal.chunkZ*16D-vz;
                float alpha=Math.max(.25F,1F-(System.currentTimeMillis()-signal.lastSeen)/(float)(cacheSeconds*1000L)*.65F);
                // A full-height outline makes the candidate chunk visible through terrain.
                AxisAlignedBB column=new AxisAlignedBB(x,-vy,z,x+16D,256D-vy,z+16D);
                RenderGlobal.drawSelectionBoundingBox(column,1F,.72F,.05F,alpha);
                AxisAlignedBB cap=new AxisAlignedBB(x,64D-vy,z,x+16D,64.12D-vy,z+16D);
                RenderGlobal.drawSelectionBoundingBox(cap,1F,.9F,.15F,Math.min(1F,alpha+.15F));
            }
        }finally{GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1F,1F,1F,1F);GlStateManager.popMatrix();GL11.glPopAttrib();}
    }

    @Override public void onOverlay(RenderGameOverlayEvent.Post event){
        if(event.getType()!=RenderGameOverlayEvent.ElementType.TEXT||mc.player==null||mc.currentScreen!=null||candidates.isEmpty())return;
        List<Signal> nearest=new ArrayList<Signal>(candidates.values());
        Collections.sort(nearest,new Comparator<Signal>(){@Override public int compare(Signal a,Signal b){return Double.compare(distanceSq(a),distanceSq(b));}});
        int y=58;mc.fontRenderer.drawStringWithShadow("BASE SIGNALS  [heuristic]",8,y,0xFFFFD700);y+=mc.fontRenderer.FONT_HEIGHT+3;
        int shown=0;
        for(Signal signal:nearest){
            if(shown>=4)break;
            if(Math.abs(signal.chunkX-mc.player.chunkCoordX)>(range+15)/16||Math.abs(signal.chunkZ-mc.player.chunkCoordZ)>(range+15)/16)continue;
            double distance=Math.sqrt(distanceSq(signal));
            String text=String.format(java.util.Locale.ROOT,"Chunk %d, %d  ·  %d objects / score %d  ·  %.0fm",signal.chunkX,signal.chunkZ,signal.count,signal.score,distance);
            mc.fontRenderer.drawStringWithShadow(text,8,y,0xFFE8D77A);y+=mc.fontRenderer.FONT_HEIGHT+2;shown++;
        }
        if(shown==0)mc.fontRenderer.drawStringWithShadow("No candidates in range",8,y,0xFFAAAAAA);
    }

    private double distanceSq(Signal signal){double x=signal.chunkX*16D+8D-mc.player.posX,z=signal.chunkZ*16D+8D-mc.player.posZ;return x*x+z*z;}
    private static long chunkKey(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}

    private static final class WorkingSignal{
        final int chunkX,chunkZ;int score,count;final List<BlockPos> examples=new ArrayList<BlockPos>();
        WorkingSignal(int chunkX,int chunkZ){this.chunkX=chunkX;this.chunkZ=chunkZ;}
    }
    private static final class Signal{
        final int chunkX,chunkZ,score,count;final long lastSeen;final List<BlockPos> examples;
        Signal(int chunkX,int chunkZ,int score,int count,long lastSeen,List<BlockPos> examples){this.chunkX=chunkX;this.chunkZ=chunkZ;this.score=score;this.count=count;this.lastSeen=lastSeen;this.examples=new ArrayList<BlockPos>(examples);}
    }
}
