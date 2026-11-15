package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.item.Item;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Incremental, configurable block ESP for targets chosen in the block catalog. */
public final class BlockESP extends Module {
    public int radius=64,scanBudget=8192,throughWalls=1;
    /** Comma-separated registry ids, persisted by ConfigManager as an ordinary module setting. */
    public String targetsCsv="";
    private String parsedTargetsCsv;
    private final Map<String,Block> selectedTargets=new LinkedHashMap<String,Block>();
    private List<Block> selectedTargetsView=java.util.Collections.emptyList();
    private java.util.Set<Block> selectedTargetSet=java.util.Collections.emptySet();
    private final Map<Long,List<BlockPos>> matches=new HashMap<Long,List<BlockPos>>();
    private final ArrayDeque<Job> queue=new ArrayDeque<Job>();
    private final Set<Long> queued=new HashSet<Long>();
    private final Map<Long,Integer> lastScanned=new HashMap<Long,Integer>();
    private Job active;
    private Object scannedWorld;
    private int scannedDimension,scannedRadius,ticks;
    private static final class Job {
        final int x,z;
        int cursor;
        final List<BlockPos> found=new ArrayList<BlockPos>();
        Job(int x,int z){this.x=x;this.z=z;}
    }

    public BlockESP(){
        super("BlockESP","Incrementally outlines the blocks selected from the searchable block catalog.",Category.RENDER,0);
        addIntSetting("Radius",16,128,16,()->radius,v->radius=v);
        addIntSetting("Blocks per tick",512,16384,512,()->scanBudget,v->scanBudget=v);
        addIntSetting("Through walls (0/1)",0,1,1,()->throughWalls,v->throughWalls=v);
    }

    public synchronized List<Block> selectedBlocks(){
        if(!java.util.Objects.equals(parsedTargetsCsv,targetsCsv))parseTargets();
        return selectedTargetsView;
    }

    public synchronized boolean isSelected(Block block){
        if(!java.util.Objects.equals(parsedTargetsCsv,targetsCsv))parseTargets();
        ResourceLocation id=Block.REGISTRY.getNameForObject(block);return id!=null&&selectedTargets.containsKey(id.toString());
    }

    /** Adds or removes the chosen block and persists the registry id immediately. */
    public synchronized void toggleTarget(Block block){
        if(block==null||block==net.minecraft.init.Blocks.AIR)return;
        ResourceLocation registryId=Block.REGISTRY.getNameForObject(block);if(registryId==null)return;
        if(!java.util.Objects.equals(parsedTargetsCsv,targetsCsv))parseTargets();
        Map<String,Block> ids=new LinkedHashMap<String,Block>(selectedTargets);String id=registryId.toString();
        if(ids.containsKey(id))ids.remove(id);else ids.put(id,block);
        StringBuilder value=new StringBuilder();for(String selected:ids.keySet()){if(value.length()>0)value.append(',');value.append(selected);}
        targetsCsv=value.toString();parseTargets();resetScan();dev.goldforge.ConfigManager.save();
    }

    @Override protected void onEnable(){resetScan();}
    @Override protected void onDisable(){resetScan();}

    @Override public void tick(){
        if(mc.player==null||mc.world==null)return;
        if(!java.util.Objects.equals(parsedTargetsCsv,targetsCsv)){parseTargets();resetScan();}
        if(scannedWorld!=mc.world||scannedDimension!=mc.player.dimension||scannedRadius!=radius){
            resetScan();scannedWorld=mc.world;scannedDimension=mc.player.dimension;scannedRadius=radius;
        }
        if(selectedTargetSet.isEmpty()){resetScan();return;}
        if(!(mc.world.getChunkProvider() instanceof ChunkProviderClient))return;
        ChunkProviderClient provider=(ChunkProviderClient)mc.world.getChunkProvider();
        if(++ticks%20==1){prune(provider);seed(provider);}
        int budget=Math.max(512,Math.min(16384,scanBudget));
        while(budget>0){
            if(active==null){active=queue.pollFirst();if(active==null)break;}
            Chunk chunk=provider.getLoadedChunk(active.x,active.z);
            if(chunk==null){finish(false);continue;}
            ExtendedBlockStorage[] sections=chunk.getBlockStorageArray();
            int section=active.cursor>>>12;
            if(section<16&&(sections[section]==null||sections[section].isEmpty())){
                active.cursor=(section+1)<<12;
                if(active.cursor==65536)finish(true);
                continue;
            }
            int index=active.cursor++,x=index&15,z=(index>>>4)&15,y=index>>>8;
            BlockPos pos=new BlockPos((active.x<<4)+x,y,(active.z<<4)+z);
            if(selectedTargetSet.contains(chunk.getBlockState(pos).getBlock())&&active.found.size()<256)
                active.found.add(pos);
            budget--;
            if(active.cursor==65536)finish(true);
        }
    }

    public void onChunkLoad(Chunk chunk){
        if(enabled&&chunk!=null&&mc.player!=null&&mc.world!=null&&inRange(chunk.x,chunk.z))enqueue(chunk.x,chunk.z);
    }

    private void seed(ChunkProviderClient provider){
        int cx=mc.player.chunkCoordX,cz=mc.player.chunkCoordZ,cr=(radius+15)/16;
        List<int[]> nearby=new ArrayList<int[]>();
        for(int dx=-cr;dx<=cr;dx++)for(int dz=-cr;dz<=cr;dz++){
            int x=cx+dx,z=cz+dz;
            if(provider.getLoadedChunk(x,z)!=null&&inRange(x,z))nearby.add(new int[]{x,z,dx*dx+dz*dz});
        }
        nearby.sort((a,b)->Integer.compare(a[2],b[2]));
        for(int[] c:nearby){
            long key=key(c[0],c[1]);Integer last=lastScanned.get(key);
            if((last==null||ticks-last>=200)&&queue.size()<512)enqueue(c[0],c[1]);
        }
    }
    private void enqueue(int x,int z){if(queued.add(key(x,z)))queue.addLast(new Job(x,z));}
    private void finish(boolean complete){
        long key=key(active.x,active.z);
        if(complete){matches.put(key,active.found);lastScanned.put(key,ticks);}
        queued.remove(key);active=null;
    }
    private void prune(ChunkProviderClient provider){
        matches.keySet().removeIf(k->!inRange(x(k),z(k))||provider.getLoadedChunk(x(k),z(k))==null);
        lastScanned.keySet().removeIf(k->!inRange(x(k),z(k))||provider.getLoadedChunk(x(k),z(k))==null);
        queue.removeIf(job->{boolean remove=!inRange(job.x,job.z)||provider.getLoadedChunk(job.x,job.z)==null;if(remove)queued.remove(key(job.x,job.z));return remove;});
        if(active!=null&&(!inRange(active.x,active.z)||provider.getLoadedChunk(active.x,active.z)==null))finish(false);
    }
    private boolean inRange(int x,int z){
        double px=mc.player.posX,pz=mc.player.posZ;
        double nx=Math.max(x*16,Math.min(px,x*16+16)),nz=Math.max(z*16,Math.min(pz,z*16+16));
        double dx=nx-px,dz=nz-pz;return dx*dx+dz*dz<=radius*radius;
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static int x(long k){return (int)(k>>32);}
    private static int z(long k){return (int)k;}

    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null||matches.isEmpty())return;
        GlStateManager.pushMatrix();
        try{
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableTexture2D();GlStateManager.disableCull();
            if(throughWalls!=0)GlStateManager.disableDepth();else GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            double vx=mc.getRenderManager().viewerPosX,vy=mc.getRenderManager().viewerPosY,vz=mc.getRenderManager().viewerPosZ;
            for(List<BlockPos> found:matches.values())for(BlockPos pos:found){
                if(pos.distanceSq(mc.player.posX,mc.player.posY,mc.player.posZ)>radius*radius)continue;
                AxisAlignedBB box=new AxisAlignedBB(pos.getX()-vx,pos.getY()-vy,pos.getZ()-vz,pos.getX()+1-vx,pos.getY()+1-vy,pos.getZ()+1-vz);
                RenderGlobal.drawSelectionBoundingBox(box,1F,.84F,0F,.95F);
            }
        }finally{
            GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1F,1F,1F,1F);GlStateManager.popMatrix();
        }
    }

    private synchronized void parseTargets(){
        parsedTargetsCsv=targetsCsv;selectedTargets.clear();
        if(targetsCsv!=null&&!targetsCsv.trim().isEmpty())for(String raw:targetsCsv.split(","))try{ResourceLocation id=new ResourceLocation(raw.trim());Block block=Block.REGISTRY.getObject(id);if(block!=null&&block!=net.minecraft.init.Blocks.AIR)selectedTargets.put(id.toString(),block);}catch(IllegalArgumentException ignored){}
        selectedTargetsView=java.util.Collections.unmodifiableList(new ArrayList<Block>(selectedTargets.values()));
        selectedTargetSet=java.util.Collections.unmodifiableSet(new java.util.HashSet<Block>(selectedTargets.values()));
    }
    private void resetScan(){scannedWorld=null;queue.clear();queued.clear();lastScanned.clear();active=null;matches.clear();ticks=0;}
}
