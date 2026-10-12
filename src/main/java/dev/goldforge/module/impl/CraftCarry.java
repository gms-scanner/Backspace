package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.network.play.client.CPacketCloseWindow;
import net.minecraft.item.ItemStack;

/** Preserves the player container when a server/client sends a close for window zero. */
public final class CraftCarry extends Module {
    private static volatile boolean keepCraftingWindow;
    public CraftCarry(){super("CraftCarry","Keeps occupied player crafting slots attached to window zero when supported by the server.",Category.PLAYER,0);}
    @Override public void tick(){
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        keepCraftingWindow=false;
        if(mc.player==null||mc.world==null||!(mc.player.openContainer instanceof ContainerPlayer))return;
        ContainerPlayer container=(ContainerPlayer)mc.player.openContainer;
        if(container.windowId!=0)return;
        // A carried cursor stack is deliberately left to vanilla close/sync handling.
        if(!mc.player.inventory.getItemStack().isEmpty())return;
        for(int slot=1;slot<=4&&slot<container.inventorySlots.size();slot++){
            ItemStack stack=container.getSlot(slot).getStack();if(stack!=null&&!stack.isEmpty()){keepCraftingWindow=true;return;}
        }
    }
    public static boolean shouldKeepOpen(CPacketCloseWindow packet){return packet!=null&&keepCraftingWindow;}
    @Override protected void onDisable(){keepCraftingWindow=false;}
    @Override protected void onWorldUnload(){keepCraftingWindow=false;}
}
