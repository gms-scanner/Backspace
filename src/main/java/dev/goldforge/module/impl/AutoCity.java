package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Mines an exposed obsidian or ender-chest city block beside the nearest enemy. */
public final class AutoCity extends Module {
    public int targetRange=5,friendFilter=1,autoTool=1;
    private BlockPos mining;

    public AutoCity(){
        super("AutoCity","Mines a reachable obsidian or ender-chest block beside the nearest player.",Category.COMBAT,0);
        addIntSetting("Target range",2,6,1,()->targetRange,v->targetRange=v);
        addIntSetting("Ignore friends (0/1)",0,1,1,()->friendFilter,v->friendFilter=v);
        addIntSetting("Select best tool (0/1)",0,1,1,()->autoTool,v->autoTool=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null){reset();return;}
        EntityPlayer target=nearestTarget();
        if(target==null){reset();return;}
        BlockPos feet=new BlockPos(target.posX,target.posY,target.posZ);
        BlockPos best=null;double bestDistance=Double.MAX_VALUE;
        for(EnumFacing side:EnumFacing.HORIZONTALS){
            BlockPos candidate=feet.offset(side);
            Block block=mc.world.getBlockState(candidate).getBlock();
            if(block!=Blocks.OBSIDIAN&&block!=Blocks.ENDER_CHEST)continue;
            Vec3d center=new Vec3d(candidate.getX()+.5D,candidate.getY()+.5D,candidate.getZ()+.5D);
            double distance=mc.player.getPositionEyes(1F).squareDistanceTo(center);
            double reach=Math.min(targetRange,mc.playerController.getBlockReachDistance());
            if(distance>reach*reach)continue;
            RayTraceResult trace=mc.world.rayTraceBlocks(mc.player.getPositionEyes(1F),center,false,true,false);
            if(trace!=null&&!candidate.equals(trace.getBlockPos()))continue;
            if(distance<bestDistance){bestDistance=distance;best=candidate;}
        }
        if(best==null){reset();return;}
        if(mining!=null&&!mining.equals(best))mc.playerController.resetBlockRemoving();
        mining=best.toImmutable();
        if(autoTool!=0){int tool=InventoryManager.bestHotbarTool(mc,mc.world.getBlockState(best));if(tool>=0)mc.player.inventory.currentItem=tool;}
        mc.playerController.onPlayerDamageBlock(best,EnumFacing.UP);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
    }

    private EntityPlayer nearestTarget(){
        EntityPlayer best=null;double bestDistance=Double.MAX_VALUE;double range=Math.min(targetRange,mc.playerController.getBlockReachDistance());
        for(EntityPlayer candidate:mc.world.playerEntities){
            if(candidate==mc.player||candidate.isDead||candidate.getHealth()<=0F||candidate.isSpectator())continue;
            if(friendFilter!=0&&FriendManager.isFriend(candidate.getName()))continue;
            double distance=mc.player.getDistanceSq(candidate);if(distance>range*range||distance>=bestDistance)continue;
            best=candidate;bestDistance=distance;
        }
        return best;
    }

    private void reset(){if(mining!=null&&mc.playerController!=null)mc.playerController.resetBlockRemoving();mining=null;}
    @Override protected void onDisable(){reset();}
    @Override protected void onWorldUnload(){reset();}
}
