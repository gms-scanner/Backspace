package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** Uses XP bottles only while worn or held Mending equipment still needs durability. */
public final class AutoMend extends Module {
    public int useDelay=4,minimumDamage=1,restoreSlot=1;
    private int cooldown,originalSlot=-1;

    public AutoMend(){
        super("AutoMend","Uses experience bottles for damaged Mending gear that is worn or held.",Category.PLAYER,0);
        addIntSetting("Use delay (ticks)",1,20,1,()->useDelay,v->useDelay=v);
        addIntSetting("Minimum durability damage",1,100,1,()->minimumDamage,v->minimumDamage=v);
        addIntSetting("Restore previous slot (0/1)",0,1,1,()->restoreSlot,v->restoreSlot=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null)return;
        if(mc.currentScreen!=null||!hasMendingDamage()){restore();return;}
        if(cooldown>0){cooldown--;return;}
        int bottle=InventoryManager.findItem(mc,Items.EXPERIENCE_BOTTLE);
        if(bottle<0){restore();return;}
        if(originalSlot<0)originalSlot=mc.player.inventory.currentItem;
        int hotbar=bottle<9?bottle:originalSlot;
        if(bottle>=9&&!InventoryManager.moveToHotbar(mc,bottle,hotbar))return;
        mc.player.inventory.currentItem=hotbar;
        ItemStack held=mc.player.getHeldItemMainhand();
        if(held.isEmpty()||held.getItem()!=Items.EXPERIENCE_BOTTLE)return;
        mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);
        cooldown=useDelay;
    }

    private boolean hasMendingDamage(){
        for(ItemStack stack:mc.player.getArmorInventoryList())if(needsRepair(stack))return true;
        return needsRepair(mc.player.getHeldItemMainhand())||needsRepair(mc.player.getHeldItemOffhand());
    }

    private boolean needsRepair(ItemStack stack){
        return stack!=null&&!stack.isEmpty()&&stack.isItemStackDamageable()&&stack.getItemDamage()>=minimumDamage&&
                EnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING,stack)>0;
    }

    private void restore(){
        if(restoreSlot!=0&&originalSlot>=0&&mc.player!=null&&originalSlot<9)mc.player.inventory.currentItem=originalSlot;
        originalSlot=-1;cooldown=0;
    }

    @Override protected void onDisable(){restore();}
    @Override protected void onWorldUnload(){restore();}
}
