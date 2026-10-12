package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Fills nearby obsidian or bedrock holes with obsidian using supported block placement. */
public final class HoleFill extends Module {
    public int range=4,delayTicks=1,placesPerTick=1,enderChestFallback=0;
    private int cooldown;

    public HoleFill(){
        super("HoleFill","Fills nearby bedrock or obsidian holes with supported obsidian placements.",Category.COMBAT,0);
        addIntSetting("Scan range",1,6,1,()->range,v->range=v);
        addIntSetting("Action delay (ticks)",0,10,1,()->delayTicks,v->delayTicks=v);
        addIntSetting("Placements per tick",1,4,1,()->placesPerTick,v->placesPerTick=v);
        addIntSetting("Use ender chest if out of obsidian (0/1)",0,1,1,()->enderChestFallback,v->enderChestFallback=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null)return;
        if(cooldown>0){cooldown--;return;}
        List<BlockPos> holes=findHoles();
        if(holes.isEmpty())return;
        int preferred=mc.player.inventory.currentItem;
        int slot=InventoryBlocks.selectBlock(mc,Blocks.OBSIDIAN,preferred);
        if(slot<0&&enderChestFallback!=0)slot=InventoryBlocks.selectBlock(mc,Blocks.ENDER_CHEST,preferred);
        if(slot<0)return;
        int placed=0;
        for(BlockPos hole:holes){
            if(placed>=placesPerTick)break;
            if(!mc.world.isAirBlock(hole)||!mc.world.isAirBlock(hole.up()))continue;
            mc.player.inventory.currentItem=slot;
            Vec3d hit=new Vec3d(hole.getX()+.5D,hole.getY()-.01D,hole.getZ()+.5D);
            double reach=mc.playerController.getBlockReachDistance();
            if(mc.player.getPositionEyes(1F).squareDistanceTo(hit)>reach*reach)continue;
            EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,hole.down(),EnumFacing.UP,hit,EnumHand.MAIN_HAND);
            if(result==EnumActionResult.SUCCESS){mc.player.swingArm(EnumHand.MAIN_HAND);placed++;}
        }
        if(placed>0)cooldown=delayTicks;
    }

    private List<BlockPos> findHoles(){
        List<BlockPos> holes=new ArrayList<BlockPos>();
        BlockPos center=new BlockPos(mc.player.posX,mc.player.posY,mc.player.posZ);
        int r=range;
        for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)for(int y=-2;y<=1;y++){
            BlockPos feet=center.add(x,y,z);
            if(!mc.world.isAirBlock(feet)||!mc.world.isAirBlock(feet.up())||!isSafeBlock(mc.world.getBlockState(feet.down()).getBlock()))continue;
            if(!isSafeBlock(mc.world.getBlockState(feet.north()).getBlock())||!isSafeBlock(mc.world.getBlockState(feet.south()).getBlock())||!isSafeBlock(mc.world.getBlockState(feet.east()).getBlock())||!isSafeBlock(mc.world.getBlockState(feet.west()).getBlock()))continue;
            holes.add(feet.toImmutable());
        }
        Collections.sort(holes,new Comparator<BlockPos>(){@Override public int compare(BlockPos a,BlockPos b){return Double.compare(mc.player.getDistanceSq(a.getX()+.5D,a.getY()+.5D,a.getZ()+.5D),mc.player.getDistanceSq(b.getX()+.5D,b.getY()+.5D,b.getZ()+.5D));}});
        return holes;
    }

    private boolean isSafeBlock(net.minecraft.block.Block block){return block==Blocks.BEDROCK||block==Blocks.OBSIDIAN;}
    @Override protected void onDisable(){cooldown=0;}
    @Override protected void onWorldUnload(){cooldown=0;}
}
