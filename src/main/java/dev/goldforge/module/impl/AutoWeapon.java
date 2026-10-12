package dev.goldforge.module.impl;

import com.google.common.collect.Multimap;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.SharedMonsterAttributes;

/** Selects the strongest held hotbar weapon while attacking a targeted entity. */
public final class AutoWeapon extends Module {
    public int restoreSlot=1;
    private int originalSlot=-1,lastSelected=-1;

    public AutoWeapon(){
        super("AutoWeapon","Selects the strongest hotbar weapon only while attacking a targeted entity.",Category.COMBAT,0);
        addIntSetting("Restore previous slot (0/1)",0,1,1,()->restoreSlot,v->restoreSlot=v);
    }

    @Override public void tick(){
        EntityPlayerSP player=mc.player;
        if(player==null||mc.world==null||mc.currentScreen!=null||!mc.gameSettings.keyBindAttack.isKeyDown()) {restore();return;}
        RayTraceResult hit=mc.objectMouseOver;
        if(hit==null||hit.typeOfHit!=RayTraceResult.Type.ENTITY||hit.entityHit==null){restore();return;}
        int best=-1;double bestScore=Double.NEGATIVE_INFINITY;
        for(int slot=0;slot<9;slot++){
            net.minecraft.item.ItemStack stack=player.inventory.getStackInSlot(slot);if(stack.isEmpty())continue;
            Multimap<String,AttributeModifier> attributes=stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND);
            double damage=1D;
            for(AttributeModifier modifier:attributes.get(SharedMonsterAttributes.ATTACK_DAMAGE.getName()))damage+=modifier.getAmount();
            damage+=EnchantmentHelper.getEnchantmentLevel(Enchantments.SHARPNESS,stack)*.5D;
            if(stack.isItemStackDamageable())damage*=Math.max(.25D,1D-stack.getItemDamage()/(double)stack.getMaxDamage()*.35D);
            if(damage>bestScore){bestScore=damage;best=slot;}
        }
        if(best<0){restore();return;}
        if(originalSlot<0)originalSlot=player.inventory.currentItem;
        player.inventory.currentItem=best;lastSelected=best;
    }

    private void restore(){
        if(mc.player!=null&&restoreSlot!=0&&originalSlot>=0&&lastSelected>=0&&mc.player.inventory.currentItem==lastSelected)mc.player.inventory.currentItem=originalSlot;
        originalSlot=-1;lastSelected=-1;
    }
    @Override protected void onDisable(){restore();}
    @Override protected void onWorldUnload(){restore();}
}
