package dev.goldforge.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class InventoryBlocks {
    private InventoryBlocks() {}

    static int selectFullBlock(Minecraft mc, int preferredSlot) {
        for (int slot=0; slot<9; slot++) {
            ItemStack stack=mc.player.inventory.getStackInSlot(slot);
            if (isFullBlock(stack)) return slot;
        }
        for (int slot=9; slot<36; slot++) {
            ItemStack stack=mc.player.inventory.getStackInSlot(slot);
            if (isFullBlock(stack)) {
                mc.playerController.windowClick(mc.player.inventoryContainer.windowId, slot, preferredSlot, ClickType.SWAP, mc.player);
                return preferredSlot;
            }
        }
        return -1;
    }

    static int selectBlock(Minecraft mc, net.minecraft.block.Block block, int preferredSlot) {
        for (int slot=0; slot<36; slot++) {
            ItemStack stack=mc.player.inventory.getStackInSlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock) || ((ItemBlock)stack.getItem()).getBlock()!=block) continue;
            if (slot<9 && slot==preferredSlot) return preferredSlot;
            if (slot<9) mc.playerController.windowClick(mc.player.inventoryContainer.windowId, 36+slot, preferredSlot, ClickType.SWAP, mc.player);
            else mc.playerController.windowClick(mc.player.inventoryContainer.windowId, slot, preferredSlot, ClickType.SWAP, mc.player);
            return preferredSlot;
        }
        return -1;
    }

    /** Wither skulls use ItemSkull metadata, not ItemBlock, so they need a dedicated lookup. */
    static int selectWitherSkull(Minecraft mc, int preferredSlot) {
        for (int slot=0; slot<36; slot++) {
            ItemStack stack=mc.player.inventory.getStackInSlot(slot);
            if (!isWitherSkull(stack)) continue;
            if (slot<9 && slot==preferredSlot) return preferredSlot;
            int containerSlot=slot<9?36+slot:slot;
            mc.playerController.windowClick(mc.player.inventoryContainer.windowId,containerSlot,preferredSlot,ClickType.SWAP,mc.player);
            return preferredSlot;
        }
        return -1;
    }

    static boolean isWitherSkull(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem()==Items.SKULL && stack.getMetadata()==1;
    }

    static Vec3d hitVec(BlockPos support,EnumFacing side) {
        return new Vec3d(support.getX()+.5D+side.getFrontOffsetX()*.5D,
                support.getY()+.5D+side.getFrontOffsetY()*.5D,
                support.getZ()+.5D+side.getFrontOffsetZ()*.5D);
    }

    private static boolean isFullBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemBlock && ((ItemBlock)stack.getItem()).getBlock().getDefaultState().isFullBlock();
    }
}
