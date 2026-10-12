package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;

/** Refills low hotbar stacks from matching inventory stacks using vanilla inventory swaps. */
public final class AutoRefill extends Module {
    public int refillAt=8,delayTicks=4;
    private int cooldown;

    public AutoRefill(){
        super("AutoRefill","Refills low hotbar stacks from matching inventory items when no screen is open.",Category.PLAYER,0);
        addIntSetting("Refill at or below",1,32,1,()->refillAt,v->refillAt=v);
        addIntSetting("Delay ticks",1,20,1,()->delayTicks,v->delayTicks=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null)return;
        if(mc.currentScreen!=null||!mc.player.inventory.getItemStack().isEmpty()){cooldown=0;return;}
        if(cooldown>0){cooldown--;return;}
        for(int hotbar=0;hotbar<9;hotbar++){
            ItemStack target=mc.player.inventory.getStackInSlot(hotbar);
            if(target.isEmpty()||target.getCount()>refillAt||!target.isStackable())continue;
            int source=findMatchingStack(target);
            if(source<0)continue;
            mc.playerController.windowClick(mc.player.inventoryContainer.windowId,source,hotbar,ClickType.SWAP,mc.player);
            cooldown=delayTicks;
            return;
        }
    }

    private int findMatchingStack(ItemStack target){
        int best=-1,bestCount=0;
        for(int slot=9;slot<36;slot++){
            ItemStack candidate=mc.player.inventory.getStackInSlot(slot);
            if(candidate.isEmpty()||candidate.getCount()<=bestCount||!ItemStack.areItemsEqual(target,candidate)||!ItemStack.areItemStackTagsEqual(target,candidate))continue;
            best=slot;bestCount=candidate.getCount();
        }
        return best;
    }

    @Override protected void onDisable(){cooldown=0;}
    @Override protected void onWorldUnload(){cooldown=0;}
}
