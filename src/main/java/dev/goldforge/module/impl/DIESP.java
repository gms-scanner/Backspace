package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import java.util.*;

/** Highlights selected dropped items among entities already loaded by the client. */
public final class DIESP extends Module {
    public String targetsCsv="minecraft:diamond,minecraft:emerald,minecraft:nether_star,minecraft:elytra,minecraft:totem_of_undying,minecraft:shulker_box";
    public int labels=1;
    private String parsed;
    private final Set<String> selected=new LinkedHashSet<String>();
    public DIESP(){
        super("DIESP","Highlights selected valuable dropped items in loaded chunks.",Category.RENDER,0);
        addIntSetting("Labels (0/1)",0,1,1,()->labels,v->labels=v);
    }
    private synchronized void parse(){
        if(Objects.equals(parsed,targetsCsv))return;
        parsed=targetsCsv;selected.clear();
        if(targetsCsv!=null)for(String raw:targetsCsv.split(","))try{
            ResourceLocation id=new ResourceLocation(raw.trim());
            if(Item.REGISTRY.getObject(id)!=null&&Item.REGISTRY.getObject(id)!=net.minecraft.init.Items.AIR)selected.add(id.toString());
        }catch(IllegalArgumentException ignored){}
    }
    public synchronized boolean isSelected(Item item){parse();ResourceLocation id=Item.REGISTRY.getNameForObject(item);return id!=null&&selected.contains(id.toString());}
    public synchronized int selectedCount(){parse();return selected.size();}
    public synchronized void toggleTarget(Item item){
        ResourceLocation id=Item.REGISTRY.getNameForObject(item);if(id==null||item==net.minecraft.init.Items.AIR)return;
        parse();if(!selected.add(id.toString()))selected.remove(id.toString());
        targetsCsv=String.join(",",selected);parsed=targetsCsv;
        dev.goldforge.ConfigManager.save();
    }
    @Override public void render(float partialTicks){
        if(mc.world==null||mc.player==null||mc.getRenderManager()==null)return;
        parse();if(selected.isEmpty())return;
        double range=Math.max(16,mc.gameSettings.renderDistanceChunks*16+16);
        CombatVisuals.begin();
        try{
            for(Entity entity:mc.world.loadedEntityList){
                if(!(entity instanceof EntityItem)||entity.isDead||mc.player.getDistanceSq(entity)>range*range)continue;
                ItemStack stack=((EntityItem)entity).getItem();if(stack.isEmpty()||!isSelected(stack.getItem()))continue;
                AxisAlignedBB box=entity.getEntityBoundingBox();
                CombatVisuals.fill(box,1F,.77F,.06F,.1F);
                CombatVisuals.outline(box,1F,.85F,.12F,.95F);
                if(labels!=0)CombatVisuals.label(stack.getDisplayName()+" x"+stack.getCount(),entity.posX,box.maxY+.35D,entity.posZ,0xFFFFD65C);
            }
        }finally{CombatVisuals.end();}
    }
}
