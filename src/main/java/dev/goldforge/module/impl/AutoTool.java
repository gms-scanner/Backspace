package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.util.math.RayTraceResult;

/** Selects the best already-held hotbar tool for the block under the crosshair. */
public final class AutoTool extends Module {
    public int restoreSlot=1;
    private int originalSlot=-1,lastSelected=-1;

    public AutoTool(){
        super("AutoTool","Selects the fastest suitable hotbar tool for the block under your crosshair.",Category.PLAYER,0);
        addIntSetting("Restore previous slot (0/1)",0,1,1,()->restoreSlot,v->restoreSlot=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.currentScreen!=null||!mc.gameSettings.keyBindAttack.isKeyDown()){restoreIfNeeded();return;}
        RayTraceResult hit=mc.objectMouseOver;
        if(hit==null||hit.typeOfHit!=RayTraceResult.Type.BLOCK){restoreIfNeeded();return;}
        int best=InventoryManager.bestHotbarTool(mc,mc.world.getBlockState(hit.getBlockPos()));
        if(best<0){restoreIfNeeded();return;}
        if(originalSlot<0)originalSlot=mc.player.inventory.currentItem;
        mc.player.inventory.currentItem=best;lastSelected=best;
    }

    private void restoreIfNeeded(){
        if(mc.player!=null&&restoreSlot!=0&&originalSlot>=0&&lastSelected>=0&&mc.player.inventory.currentItem==lastSelected)mc.player.inventory.currentItem=originalSlot;
        originalSlot=-1;lastSelected=-1;
    }

    @Override protected void onDisable(){restoreIfNeeded();}
    @Override protected void onWorldUnload(){restoreIfNeeded();}
}
