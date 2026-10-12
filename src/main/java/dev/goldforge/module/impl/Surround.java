package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Rapidly restores the player ring and a mined floor using normal supported placements. */
public final class Surround extends Module {
    public int blocksPerTick=4;
    private int ticks;
    private final Map<BlockPos,Integer> pending=new HashMap<BlockPos,Integer>();
    public Surround() {
        super("Surround", "Rapidly places supported blocks around your feet and replaces a broken floor.", Category.COMBAT, 0);
        addIntSetting("Blocks per tick",1,5,1,()->blocksPerTick,v->blocksPerTick=v);
    }

    @Override public void tick() {
        if(mc.player==null||mc.world==null||mc.playerController==null)return;
        ticks++;
        Iterator<Map.Entry<BlockPos,Integer>> iterator=pending.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<BlockPos,Integer> entry=iterator.next();
            if(!mc.world.isAirBlock(entry.getKey())||ticks-entry.getValue()>2)iterator.remove();
        }
        BlockPos base=new BlockPos(mc.player.posX,mc.player.posY,mc.player.posZ);
        BlockPos[] targets={base.down(),base.north(),base.south(),base.east(),base.west()};
        int slot=InventoryBlocks.selectFullBlock(mc,mc.player.inventory.currentItem);
        if(slot<0)return;
        int placed=0;
        for(BlockPos target:targets){
            if(placed>=blocksPerTick)break;
            if(!mc.world.isAirBlock(target))continue;
            Integer last=pending.get(target);
            if(last!=null&&ticks-last<=2)continue;
            if(placeFromNeighbor(target,slot)){pending.put(target.toImmutable(),ticks);placed++;}
        }
    }

    private boolean placeFromNeighbor(BlockPos target,int slot){
        for(EnumFacing towardSupport:EnumFacing.values()){
            BlockPos support=target.offset(towardSupport);
            if(mc.world.isAirBlock(support)||!mc.world.getBlockState(support).isFullBlock())continue;
            EnumFacing clickFace=towardSupport.getOpposite();
            Vec3d hit=InventoryBlocks.hitVec(support,clickFace);
            double reach=mc.playerController.getBlockReachDistance();
            if(mc.player.getPositionEyes(1.0F).squareDistanceTo(hit)>reach*reach)continue;
            mc.player.inventory.currentItem=slot;
            EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,support,clickFace,hit,EnumHand.MAIN_HAND);
            if(result==EnumActionResult.SUCCESS){mc.player.swingArm(EnumHand.MAIN_HAND);return true;}
        }
        return false;
    }

    @Override protected void onDisable(){pending.clear();}
}
