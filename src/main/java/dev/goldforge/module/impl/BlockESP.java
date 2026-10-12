package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Incremental, configurable block ESP for targets chosen in the block catalog. */
public final class BlockESP extends Module {
    public int radius=16,scanBudget=512,throughWalls=0;
    /** Comma-separated registry ids, persisted by ConfigManager as an ordinary module setting. */
    public String targetsCsv="";
    private String parsedTargetsCsv;
    private final Map<String,Block> selectedTargets=new LinkedHashMap<String,Block>();
    private List<Block> selectedTargetsView=java.util.Collections.emptyList();
    private java.util.Set<Block> selectedTargetSet=java.util.Collections.emptySet();
    private final LinkedHashSet<BlockPos> matches=new LinkedHashSet<BlockPos>();
    private Object scannedWorld;
    private int scannedDimension,scannedRadius,scanIndex,volume,width,height,minX,minY,minZ;

    public BlockESP(){
        super("BlockESP","Incrementally outlines the blocks selected from the searchable block catalog.",Category.RENDER,0);
        addIntSetting("Radius",8,32,8,()->radius,v->radius=v);
        addIntSetting("Blocks per tick",128,1024,128,()->scanBudget,v->scanBudget=v);
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
    @Override protected void onDisable(){matches.clear();resetScan();}

    @Override public void tick(){
        if(mc.player==null||mc.world==null)return;
        if(scannedWorld!=mc.world||scannedDimension!=mc.player.dimension||scannedRadius!=radius||volume==0)beginCycle();
        if(selectedTargetSet.isEmpty()){matches.clear();scanIndex=volume;return;}
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        int end=Math.min(volume,scanIndex+Math.max(1,scanBudget));
        while(scanIndex<end){
            int index=scanIndex++;
            pos.setPos(minX+index%width,minY+index/(width*width),minZ+(index/width)%width);
            if(mc.world.isBlockLoaded(pos)&&selectedTargetSet.contains(mc.world.getBlockState(pos).getBlock()))addMatch(new BlockPos(pos));
        }
        if(scanIndex>=volume){prune();beginCycle();}
    }

    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null||matches.isEmpty())return;
        GlStateManager.pushMatrix();
        try{
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableTexture2D();GlStateManager.disableCull();
            if(throughWalls!=0)GlStateManager.disableDepth();else GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            double vx=mc.getRenderManager().viewerPosX,vy=mc.getRenderManager().viewerPosY,vz=mc.getRenderManager().viewerPosZ;
            for(BlockPos pos:matches){
                if(pos.getDistance((int)mc.player.posX,(int)mc.player.posY,(int)mc.player.posZ)>radius+2)continue;
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
    private void addMatch(BlockPos pos){if(matches.add(pos)&&matches.size()>256){Iterator<BlockPos> iterator=matches.iterator();if(iterator.hasNext()){iterator.next();iterator.remove();}}}
    private void prune(){Iterator<BlockPos> iterator=matches.iterator();while(iterator.hasNext()){BlockPos pos=iterator.next();if(pos.getDistance((int)mc.player.posX,(int)mc.player.posY,(int)mc.player.posZ)>radius+2||!mc.world.isBlockLoaded(pos)||!selectedTargetSet.contains(mc.world.getBlockState(pos).getBlock()))iterator.remove();}}
    private void beginCycle(){width=radius*2+1;height=32;int cx=(int)Math.floor(mc.player.posX),cy=(int)Math.floor(mc.player.posY),cz=(int)Math.floor(mc.player.posZ);minX=cx-radius;minY=Math.max(0,Math.min(256,cy-16));minZ=cz-radius;volume=width*width*Math.min(height,256-minY);scanIndex=0;scannedWorld=mc.world;scannedDimension=mc.player.dimension;scannedRadius=radius;}
    private void resetScan(){scannedWorld=null;scanIndex=0;volume=0;matches.clear();}
}
