package dev.goldforge.module.impl;

import dev.goldforge.module.*;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class SelfTrap extends Module {
    private int delay;
    public SelfTrap() { super("SelfTrap", "Places a protective block above your head with adjacent support.", Category.COMBAT, 0); }
    @Override public void tick() {
        if(mc.player==null||mc.world==null||delay-->0)return;delay=4;
        BlockPos target=new BlockPos(mc.player.posX,mc.player.posY+2,mc.player.posZ);if(!mc.world.isAirBlock(target))return;
        int slot=InventoryBlocks.selectFullBlock(mc,mc.player.inventory.currentItem);if(slot<0)return;
        for(EnumFacing f:EnumFacing.values()){BlockPos support=target.offset(f);if(!mc.world.isAirBlock(support)&&mc.world.getBlockState(support).isFullBlock()){mc.player.inventory.currentItem=slot;EnumFacing clickFace=f.getOpposite();mc.playerController.processRightClickBlock(mc.player,mc.world,support,clickFace,InventoryBlocks.hitVec(support,clickFace),EnumHand.MAIN_HAND);return;}}
    }
}
