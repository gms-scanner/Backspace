package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;

/** Announces once when an equipped or carried damageable item crosses a durability threshold. */
public final class DurabilityAlert extends Module {
    public int thresholdPercent=20;
    private final ItemStack[] trackedStacks=new ItemStack[41];
    private final boolean[] alerted=new boolean[41];
    private int messageCooldown;

    public DurabilityAlert(){
        super("DurabilityAlert","Sends one local chat warning when gear or tools reach low durability.",Category.PLAYER,0);
        addIntSetting("Warn at remaining durability",5,60,5,()->thresholdPercent,v->thresholdPercent=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null)return;
        if(messageCooldown>0)messageCooldown--;
        for(int slot=0;slot<41;slot++){
            ItemStack stack=getStack(slot);
            if(trackedStacks[slot]!=stack){trackedStacks[slot]=stack;alerted[slot]=false;}
            if(stack.isEmpty()||!stack.isItemStackDamageable())continue;
            int remaining=stack.getMaxDamage()-stack.getItemDamage();
            int percent=(int)Math.ceil(remaining*100.0D/stack.getMaxDamage());
            if(percent>thresholdPercent){alerted[slot]=false;continue;}
            if(alerted[slot])continue;
            if(messageCooldown>0)continue;
            mc.player.sendMessage(new TextComponentString("§e[Backspace] §f"+stack.getDisplayName()+" §elow durability: "+percent+"%"));
            alerted[slot]=true;
            messageCooldown=20;
        }
    }

    private ItemStack getStack(int slot){
        if(slot<36)return mc.player.inventory.getStackInSlot(slot);
        if(slot<40){int index=slot-36;for(ItemStack armor:mc.player.getArmorInventoryList())if(index--==0)return armor;}
        return slot==40?mc.player.getHeldItemOffhand():ItemStack.EMPTY;
    }

    private void reset(){java.util.Arrays.fill(trackedStacks,ItemStack.EMPTY);java.util.Arrays.fill(alerted,false);messageCooldown=0;}
    @Override protected void onDisable(){reset();}
    @Override protected void onWorldUnload(){reset();}
}
