package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

/** Equips an armor upgrade from the player's inventory using ordinary inventory clicks. */
public final class AutoArmor extends Module {
    public int delayTicks=4;
    private int cooldown;

    public AutoArmor(){
        super("AutoArmor","Equips better armor from your inventory while no GUI is open.",Category.PLAYER,0);
        addIntSetting("Swap delay ticks",1,20,1,()->delayTicks,v->delayTicks=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null)return;
        if(mc.currentScreen!=null||!mc.player.inventory.getItemStack().isEmpty()){cooldown=0;return;}
        if(cooldown>0){cooldown--;return;}
        for(int invSlot=0;invSlot<36;invSlot++){
            ItemStack candidate=mc.player.inventory.getStackInSlot(invSlot);
            if(candidate.isEmpty()||!(candidate.getItem() instanceof ItemArmor))continue;
            ItemArmor armor=(ItemArmor)candidate.getItem();
            int armorSlot=containerSlot(armor.armorType);
            if(armorSlot<0)continue;
            ItemStack current=mc.player.inventoryContainer.getSlot(armorSlot).getStack();
            if(score(candidate)<=score(current))continue;
            int sourceSlot=containerSlotForInventory(invSlot);
            mc.playerController.windowClick(mc.player.inventoryContainer.windowId,sourceSlot,0,ClickType.PICKUP,mc.player);
            mc.playerController.windowClick(mc.player.inventoryContainer.windowId,armorSlot,0,ClickType.PICKUP,mc.player);
            if(!current.isEmpty())mc.playerController.windowClick(mc.player.inventoryContainer.windowId,sourceSlot,0,ClickType.PICKUP,mc.player);
            cooldown=delayTicks;
            return;
        }
    }

    private float score(ItemStack stack){
        if(stack.isEmpty()||!(stack.getItem() instanceof ItemArmor))return 0F;
        ItemArmor armor=(ItemArmor)stack.getItem();
        float value=armor.getArmorMaterial().getDamageReductionAmount(armor.armorType);
        value+=EnchantmentHelper.getEnchantmentLevel(Enchantments.PROTECTION,stack)*.35F;
        value+=EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING,stack)*.05F;
        if(stack.isItemStackDamageable())value*=Math.max(.1F,1F-stack.getItemDamage()/(float)stack.getMaxDamage());
        return value;
    }

    private int containerSlot(EntityEquipmentSlot slot){
        switch(slot){case HEAD:return 5;case CHEST:return 6;case LEGS:return 7;case FEET:return 8;default:return -1;}
    }

    private int containerSlotForInventory(int inventorySlot){return inventorySlot<9?36+inventorySlot:inventorySlot;}
    @Override protected void onDisable(){cooldown=0;}
    @Override protected void onWorldUnload(){cooldown=0;}
}
