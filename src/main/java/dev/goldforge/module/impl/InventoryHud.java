package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/** Full 36-slot main inventory (hotbar and storage) with stack overlays. */
public final class InventoryHud extends Module {
    public int x=8,y=110;
    public static final int WIDTH=172,HEIGHT=88;
    public InventoryHud(){super("Inventory HUD","Shows all 36 inventory slots and stack counts.",Category.HUD,0);}
    @Override public void onOverlay(RenderGameOverlayEvent.Post event){
        if(event.getType()!=RenderGameOverlayEvent.ElementType.TEXT||mc.currentScreen instanceof dev.goldforge.gui.HudEditor)return;
        draw(x,y);
    }
    public void draw(int left,int top){
        Gui.drawRect(left,top,left+WIDTH,top+HEIGHT,0xD914171B);
        Gui.drawRect(left,top,left+WIDTH,top+12,0xEE30343A);
        mc.fontRenderer.drawStringWithShadow("INVENTORY",left+5,top+2,0xFFFFD700);
        for(int row=0;row<4;row++)for(int col=0;col<9;col++){
            int slot=row==3?col:9+row*9+col;
            int sx=left+5+col*18,sy=top+14+row*18;
            Gui.drawRect(sx,sy,sx+18,sy+18,0xA930353B);
            if(mc.player==null)continue;
            ItemStack stack=mc.player.inventory.getStackInSlot(slot);
            if(stack.isEmpty())continue;
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack,sx+1,sy+1);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer,stack,sx+1,sy+1,null);
        }
    }
}
