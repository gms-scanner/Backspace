package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumHand;

/** Throws a splash healing potion at low health using a brief server-side downward aim. */
public final class AutoPot extends Module {
    public int healthThreshold=10,cooldownTicks=16,aimPitch=85;
    private int cooldown;

    public AutoPot(){
        super("AutoPot","Throws an instant-health splash potion when health falls below the threshold.",Category.COMBAT,0);
        addIntSetting("Health threshold",4,20,1,()->healthThreshold,v->healthThreshold=v);
        addIntSetting("Cooldown ticks",4,60,1,()->cooldownTicks,v->cooldownTicks=v);
        addIntSetting("Throw pitch",70,90,1,()->aimPitch,v->aimPitch=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null)return;
        if(cooldown>0){cooldown--;return;}
        if(mc.player.getHealth()+mc.player.getAbsorptionAmount()>healthThreshold||mc.player.isHandActive()||mc.player.isElytraFlying())return;
        int slot=findHealingPotion();if(slot<0)return;
        int previous=mc.player.inventory.currentItem;
        mc.player.inventory.currentItem=slot;
        ItemStack potion=mc.player.getHeldItemMainhand();
        if(potion.isEmpty()||!isHealingPotion(potion)){mc.player.inventory.currentItem=previous;return;}
        float yaw=mc.player.rotationYaw,pitch=mc.player.rotationPitch;
        if(mc.getConnection()!=null)PacketManager.send(new CPacketPlayer.Rotation(yaw,aimPitch,mc.player.onGround),name);
        mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);
        if(mc.getConnection()!=null)PacketManager.send(new CPacketPlayer.Rotation(yaw,pitch,mc.player.onGround),name);
        mc.player.inventory.currentItem=previous;
        mc.player.swingArm(EnumHand.MAIN_HAND);
        cooldown=cooldownTicks;
    }

    private int findHealingPotion(){for(int slot=0;slot<9;slot++){ItemStack stack=mc.player.inventory.getStackInSlot(slot);if(isHealingPotion(stack))return slot;}return -1;}
    private boolean isHealingPotion(ItemStack stack){
        if(stack==null||stack.isEmpty()||stack.getItem()!=Items.SPLASH_POTION)return false;
        for(PotionEffect effect:PotionUtils.getEffectsFromStack(stack))if(effect.getPotion()==MobEffects.INSTANT_HEALTH)return true;
        return false;
    }
    @Override protected void onDisable(){cooldown=0;}
    @Override protected void onWorldUnload(){cooldown=0;}
}
