package dev.goldforge.module.impl;
import dev.goldforge.inventory.InventoryManager;

import dev.goldforge.module.*;
import net.minecraft.init.Items;
import net.minecraft.util.EnumHand;

public final class AutoLava extends Module {
    public AutoLava() { super("AutoLava", "Places lava at the block currently under your crosshair.", Category.WORLD, 0); }
    @Override public void tick() { if(mc.player==null||mc.world==null||mc.playerController==null||mc.objectMouseOver==null||mc.objectMouseOver.getBlockPos()==null)return; int slot=InventoryManager.findItem(mc,Items.LAVA_BUCKET);if(slot<0)return;int hotbar=slot<9?slot:mc.player.inventory.currentItem;if(slot>=9&&!InventoryManager.moveToHotbar(mc,slot,hotbar))return;mc.player.inventory.currentItem=hotbar;mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);setEnabled(false); }
}

