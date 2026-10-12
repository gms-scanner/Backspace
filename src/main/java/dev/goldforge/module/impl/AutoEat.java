package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** Eats the best available food when hunger falls below the configured threshold. */
public final class AutoEat extends Module {
    public int hungerThreshold=16,allowGoldenApple=0;
    private int returnSlot=-1,retryTicks;
    private boolean startedEating;

    public AutoEat(){
        super("AutoEat","Uses food from the full inventory when hunger is low; restores your selected slot afterward.",Category.PLAYER,0);
        addIntSetting("Hunger threshold",6,20,1,()->hungerThreshold,v->hungerThreshold=v);
        addIntSetting("Allow golden apples (0/1)",0,1,1,()->allowGoldenApple,v->allowGoldenApple=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null)return;
        if(mc.currentScreen!=null){stopEating();return;}
        if(mc.player.isHandActive()){
            if(mc.player.getActiveHand()==EnumHand.MAIN_HAND&&mc.player.getHeldItemMainhand().getItem() instanceof net.minecraft.item.ItemFood)startedEating=true;
            return;
        }
        if(mc.player.getFoodStats().getFoodLevel()>=hungerThreshold){stopEating();return;}
        if(retryTicks>0){retryTicks--;return;}
        int slot=InventoryManager.findBestFood(mc,allowGoldenApple!=0);
        if(slot<0){stopEating();return;}
        if(returnSlot<0)returnSlot=mc.player.inventory.currentItem;
        int hotbar=slot<9?slot:returnSlot;
        if(slot>=9&&!InventoryManager.moveToHotbar(mc,slot,hotbar)){stopEating();return;}
        mc.player.inventory.currentItem=hotbar;
        ItemStack held=mc.player.getHeldItemMainhand();
        if(held.isEmpty()||!(held.getItem() instanceof net.minecraft.item.ItemFood)){retryTicks=3;return;}
        mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);
        startedEating=mc.player.isHandActive()&&mc.player.getActiveHand()==EnumHand.MAIN_HAND;
        retryTicks=startedEating?0:3;
    }

    private void stopEating(){
        if(mc.player!=null&&startedEating&&mc.player.isHandActive()&&mc.player.getActiveHand()==EnumHand.MAIN_HAND)mc.playerController.onStoppedUsingItem(mc.player);
        if(returnSlot>=0&&mc.player!=null&&returnSlot<9)mc.player.inventory.currentItem=returnSlot;
        startedEating=false;returnSlot=-1;retryTicks=0;
    }

    @Override protected void onDisable(){stopEating();}
    @Override protected void onWorldUnload(){stopEating();}
}
