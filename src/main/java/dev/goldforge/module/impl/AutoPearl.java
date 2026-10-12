package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** Throws a hotbar ender pearl during a fall when the player's view is already aimed down. */
public final class AutoPearl extends Module {
    public int fallThreshold=5,maxFallDistance=24,minDownPitch=40,cooldownTicks=60;
    private int cooldown,returnSlot=-1;

    public AutoPearl(){
        super("AutoPearl","Uses a hotbar pearl during a dangerous fall when you are aiming down.",Category.PLAYER,0);
        addIntSetting("Fall threshold",2,20,1,()->fallThreshold,v->fallThreshold=v);
        addIntSetting("Maximum fall distance",4,80,1,()->maxFallDistance,v->maxFallDistance=v);
        addIntSetting("Minimum downward pitch",0,90,1,()->minDownPitch,v->minDownPitch=v);
        addIntSetting("Cooldown ticks",10,200,5,()->cooldownTicks,v->cooldownTicks=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null){restore();return;}
        if(cooldown>0){cooldown--;return;}
        if(mc.player.isRiding()||mc.player.isElytraFlying()||mc.player.isInWater()||mc.player.onGround)return;
        float fall=mc.player.fallDistance;
        if(fall<fallThreshold||fall>maxFallDistance||mc.player.rotationPitch<minDownPitch)return;
        int pearl=InventoryManager.findItemInHotbar(mc,Items.ENDER_PEARL);
        if(pearl<0)return;
        returnSlot=mc.player.inventory.currentItem;
        mc.player.inventory.currentItem=pearl;
        ItemStack stack=mc.player.getHeldItemMainhand();
        if(stack.isEmpty()||stack.getItem()!=Items.ENDER_PEARL){restore();return;}
        mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);
        cooldown=cooldownTicks;
        restore();
    }

    private void restore(){if(returnSlot>=0&&mc.player!=null)mc.player.inventory.currentItem=returnSlot;returnSlot=-1;}
    @Override protected void onDisable(){restore();cooldown=0;}
    @Override protected void onWorldUnload(){restore();cooldown=0;}
}
