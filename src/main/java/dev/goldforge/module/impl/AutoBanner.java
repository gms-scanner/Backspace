package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;

/** Places an inventory banner on the block under the crosshair while the player moves. */
public final class AutoBanner extends Module {
    public int actionDelay=4;
    private int cooldown;
    private double lastX,lastZ;
    private boolean hasPosition;

    public AutoBanner() {
        super("AutoBanner", "Places a banner on the block you are aiming at while moving; does not rotate your view.", Category.MISC, 0);
        addIntSetting("Action delay (ticks)", 1, 20, 1, () -> actionDelay, value -> actionDelay=value);
    }

    @Override protected void onEnable() {
        cooldown=0;
        hasPosition=mc.player!=null;
        if(hasPosition) { lastX=mc.player.posX; lastZ=mc.player.posZ; }
    }

    @Override public void tick() {
        if(mc.player==null||mc.world==null)return;
        boolean moved=hasPosition && (mc.player.posX-lastX)*(mc.player.posX-lastX)+(mc.player.posZ-lastZ)*(mc.player.posZ-lastZ)>.0004D;
        lastX=mc.player.posX; lastZ=mc.player.posZ; hasPosition=true;
        if(cooldown>0)cooldown--;
        if(!moved||cooldown>0||mc.objectMouseOver==null||mc.objectMouseOver.typeOfHit!=RayTraceResult.Type.BLOCK||mc.objectMouseOver.getBlockPos()==null)return;
        int slot=findBanner();
        if(slot<0)return;
        if(mc.world.isAirBlock(mc.objectMouseOver.getBlockPos()))return;
        mc.player.inventory.currentItem=slot;
        EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,mc.objectMouseOver.getBlockPos(),mc.objectMouseOver.sideHit,mc.objectMouseOver.hitVec,EnumHand.MAIN_HAND);
        if(result==EnumActionResult.SUCCESS) {
            mc.player.swingArm(EnumHand.MAIN_HAND);
            cooldown=actionDelay;
        }
    }

    private int findBanner() {
        int slot=InventoryManager.findItem(mc,Items.BANNER);if(slot<0)return -1;
        if(slot<9)return slot;int preferred=mc.player.inventory.currentItem;
        return InventoryManager.moveToHotbar(mc,slot,preferred)?preferred:-1;
    }
}

