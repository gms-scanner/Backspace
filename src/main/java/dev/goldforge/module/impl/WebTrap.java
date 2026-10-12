package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.player.EntityPlayer;

/** Places cobwebs around a nearby enemy through ordinary block interaction. */
public final class WebTrap extends Module {
    public int targetRange=4,delayTicks=6,friendFilter=1;
    private int cooldown;

    public WebTrap(){
        super("WebTrap","Places cobwebs at reachable feet or head positions around a nearby enemy.",Category.COMBAT,0);
        addIntSetting("Target range",2,6,1,()->targetRange,v->targetRange=v);
        addIntSetting("Action delay (ticks)",1,30,1,()->delayTicks,v->delayTicks=v);
        addIntSetting("Ignore friends (0/1)",0,1,1,()->friendFilter,v->friendFilter=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null)return;
        if(cooldown>0){cooldown--;return;}
        EntityPlayer target=nearestTarget();if(target==null)return;
        int slot=InventoryBlocks.selectBlock(mc,Blocks.WEB,mc.player.inventory.currentItem);if(slot<0)return;
        BlockPos feet=new BlockPos(target.posX,target.posY,target.posZ);
        BlockPos[] candidates={feet,feet.up(),feet.north(),feet.south(),feet.east(),feet.west()};
        double reach=mc.playerController.getBlockReachDistance();
        for(BlockPos place:candidates){
            if(!mc.world.isAirBlock(place))continue;
            for(EnumFacing supportSide:EnumFacing.values()){
                BlockPos support=place.offset(supportSide);
                if(mc.world.isAirBlock(support)||!mc.world.getBlockState(support).isFullBlock())continue;
                EnumFacing face=supportSide.getOpposite();
                Vec3d hit=InventoryBlocks.hitVec(support,face);
                if(mc.player.getPositionEyes(1F).squareDistanceTo(hit)>reach*reach)continue;
                mc.player.inventory.currentItem=slot;
                EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,support,face,hit,EnumHand.MAIN_HAND);
                if(result==EnumActionResult.SUCCESS){mc.player.swingArm(EnumHand.MAIN_HAND);cooldown=delayTicks;return;}
            }
        }
    }

    private EntityPlayer nearestTarget(){
        EntityPlayer best=null;double nearest=Double.MAX_VALUE;double range=Math.min(targetRange,mc.playerController.getBlockReachDistance());
        for(EntityPlayer player:mc.world.playerEntities){
            if(player==mc.player||player.isDead||player.getHealth()<=0F||player.isSpectator())continue;
            if(friendFilter!=0&&FriendManager.isFriend(player.getName()))continue;
            double distance=mc.player.getDistanceSq(player);if(distance>range*range||distance>=nearest)continue;
            best=player;nearest=distance;
        }
        return best;
    }
    @Override protected void onDisable(){cooldown=0;}
    @Override protected void onWorldUnload(){cooldown=0;}
}
