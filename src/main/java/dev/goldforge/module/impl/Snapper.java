package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.module.MovementManager;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

/** Finds a nearby one-block hole, steers the player over it, then lets gravity drop them in. */
public final class Snapper extends Module {
    public int scanRange=16,snapSpeed=8;
    private BlockPos target;
    private int scanDelay;
    public Snapper(){
        super("Snapper","Moves toward a nearby 1x1 hole, preferring bedrock holes, and drops you in.",Category.MOVEMENT,0);
        addIntSetting("Search range",4,32,4,()->scanRange,v->scanRange=v);
        addIntSetting("Snap speed",1,12,1,()->snapSpeed,v->snapSpeed=v);
    }
    @Override protected void onEnable(){target=null;scanDelay=0;}
    @Override public void tick(){
        if(mc.player==null||mc.world==null)return;
        if(scanDelay--<=0||!isHole(target)){target=findHole();scanDelay=10;}
        if(target==null)return;
        double dx=target.getX()+.5D-mc.player.posX,dz=target.getZ()+.5D-mc.player.posZ;
        double distance=Math.sqrt(dx*dx+dz*dz);
        if(distance<.14D){MovementManager.requestHorizontalVelocity(this,mc.player.motionX*.25D,mc.player.motionZ*.25D,20);return;}
        double max=snapSpeed*.025D;
        double scale=Math.min(max,distance*.22D)/distance;
        MovementManager.requestHorizontalVelocity(this,dx*scale,dz*scale,20);
    }
    private BlockPos findHole(){
        BlockPos origin=new BlockPos(mc.player.posX,mc.player.posY,mc.player.posZ),best=null;
        double bestScore=Double.MAX_VALUE;
        for(BlockPos pos:BlockPos.getAllInBox(origin.add(-scanRange,-4,-scanRange),origin.add(scanRange,4,scanRange))){
            double distance=mc.player.getDistanceSqToCenter(pos);
            if(distance>scanRange*scanRange||!isHole(pos))continue;
            boolean bedrock=isBedrockHole(pos);
            double score=distance-(bedrock?3.0D:0.0D);
            if(score<bestScore){bestScore=score;best=pos.toImmutable();}
        }
        return best;
    }
    private boolean isHole(BlockPos pos){
        if(pos==null||!mc.world.isAirBlock(pos)||!mc.world.isAirBlock(pos.up())||mc.world.isAirBlock(pos.down())||!mc.world.getBlockState(pos.down()).isFullBlock())return false;
        for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.HORIZONTALS){
            BlockPos wall=pos.offset(side);
            if(mc.world.isAirBlock(wall)||!mc.world.getBlockState(wall).isFullBlock())return false;
        }
        return true;
    }
    private boolean isBedrockHole(BlockPos pos){
        if(mc.world.getBlockState(pos.down()).getBlock()!=Blocks.BEDROCK)return false;
        for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.HORIZONTALS)if(mc.world.getBlockState(pos.offset(side)).getBlock()!=Blocks.BEDROCK)return false;
        return true;
    }
    @Override protected void onDisable(){target=null;}
}
