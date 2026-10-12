package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/** Breaks cobweb blocks intersecting the player's body through normal block-damage handling. */
public final class AntiWeb extends Module {
    public int autoTool=1;

    public AntiWeb(){
        super("AntiWeb","Automatically breaks cobwebs intersecting your hitbox.",Category.COMBAT,0);
        addIntSetting("Select best tool (0/1)",0,1,1,()->autoTool,v->autoTool=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null)return;
        AxisAlignedBB box=mc.player.getEntityBoundingBox();
        int minX=(int)Math.floor(box.minX),maxX=(int)Math.floor(box.maxX);
        int minY=(int)Math.floor(box.minY),maxY=(int)Math.floor(box.maxY);
        int minZ=(int)Math.floor(box.minZ),maxZ=(int)Math.floor(box.maxZ);
        BlockPos target=null;double nearest=Double.MAX_VALUE;
        for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
            BlockPos pos=new BlockPos(x,y,z);
            if(mc.world.getBlockState(pos).getBlock()!=Blocks.WEB)continue;
            double distance=mc.player.getDistanceSq(pos.getX()+.5D,pos.getY()+.5D,pos.getZ()+.5D);
            if(distance<nearest){nearest=distance;target=pos;}
        }
        if(target==null)return;
        if(autoTool!=0){int tool=InventoryManager.bestHotbarTool(mc,mc.world.getBlockState(target));if(tool>=0)mc.player.inventory.currentItem=tool;}
        mc.playerController.onPlayerDamageBlock(target,EnumFacing.UP);
        mc.player.swingArm(EnumHand.MAIN_HAND);
    }
}
