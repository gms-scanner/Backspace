package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Builds a configurable obsidian cage around the nearest reachable non-friend player. */
public final class AutoTrap extends Module {
    /** 0 full cage, 1 cage without roof, 2 feet ring. */
    public int targetRange=4,blocksPerTick=2,friendFilter=1,mode=0;
    private int cooldown;

    public AutoTrap(){
        super("AutoTrap","Places a supported obsidian cage around a nearby player.",Category.COMBAT,0);
        addIntSetting("Target range",2,6,1,()->targetRange,v->targetRange=v);
        addIntSetting("Blocks per tick",1,4,1,()->blocksPerTick,v->blocksPerTick=v);
        addIntSetting("Ignore friends (0/1)",0,1,1,()->friendFilter,v->friendFilter=v);
        addIntSetting("Mode (0 full, 1 no roof, 2 feet)",0,2,1,()->mode,v->mode=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null)return;
        if(cooldown>0){cooldown--;return;}
        EntityPlayer target=nearestTarget();if(target==null)return;
        int preferred=mc.player.inventory.currentItem;
        int slot=InventoryBlocks.selectBlock(mc,Blocks.OBSIDIAN,preferred);
        if(slot<0)return;
        BlockPos feet=new BlockPos(target.posX,target.posY,target.posZ);
        BlockPos[] ring={feet.north(),feet.south(),feet.east(),feet.west()};
        int placed=0;
        for(BlockPos pos:ring){if(placed>=blocksPerTick)break;if(placeSupported(pos,slot))placed++;}
        if(mode!=2&&placed<blocksPerTick)for(BlockPos pos:ring){if(placed>=blocksPerTick)break;if(placeSupported(pos.up(),slot))placed++;}
        if(mode==0&&placed<blocksPerTick&&placeSupported(feet.up(2),slot))placed++;
        if(placed>0)cooldown=1;
    }

    private boolean placeSupported(BlockPos target,int slot){
        if(!mc.world.isAirBlock(target))return false;
        double reach=mc.playerController.getBlockReachDistance();
        for(EnumFacing side:EnumFacing.values()){
            BlockPos support=target.offset(side);
            if(mc.world.isAirBlock(support)||!mc.world.getBlockState(support).isFullBlock())continue;
            EnumFacing face=side.getOpposite();Vec3d hit=InventoryBlocks.hitVec(support,face);
            if(mc.player.getPositionEyes(1F).squareDistanceTo(hit)>reach*reach)continue;
            mc.player.inventory.currentItem=slot;
            EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,support,face,hit,EnumHand.MAIN_HAND);
            if(result==EnumActionResult.SUCCESS){mc.player.swingArm(EnumHand.MAIN_HAND);return true;}
        }
        return false;
    }

    private EntityPlayer nearestTarget(){
        EntityPlayer best=null;double nearest=Double.MAX_VALUE;double reach=Math.min(targetRange,mc.playerController.getBlockReachDistance());
        for(EntityPlayer player:mc.world.playerEntities){
            if(player==mc.player||player.isDead||player.getHealth()<=0F||player.isSpectator())continue;
            if(friendFilter!=0&&FriendManager.isFriend(player.getName()))continue;
            double distance=mc.player.getDistanceSq(player);if(distance>reach*reach||distance>=nearest)continue;
            best=player;nearest=distance;
        }
        return best;
    }
    @Override protected void onDisable(){cooldown=0;}
    @Override protected void onWorldUnload(){cooldown=0;}
}
