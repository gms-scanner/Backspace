package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.FriendManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;

/** Uses XP bottles only while worn or held Mending equipment still needs durability. */
public final class AutoMend extends Module {
    public int useDelay=4,minimumDamage=1,restoreSlot=1,pauseNearPlayers=1,enemyRange=12,safeHealth=18;
    private int cooldown,originalSlot=-1,bottleSlot=-1,movedFrom=-1;

    public AutoMend(){
        super("AutoMend","Uses experience bottles for damaged Mending gear that is worn or held.",Category.PLAYER,0);
        addIntSetting("Use delay (ticks)",1,20,1,()->useDelay,v->useDelay=v);
        addIntSetting("Minimum durability damage",1,100,1,()->minimumDamage,v->minimumDamage=v);
        addIntSetting("Restore previous slot (0/1)",0,1,1,()->restoreSlot,v->restoreSlot=v);
        addIntSetting("Pause near enemies (0/1)",0,1,1,()->pauseNearPlayers,v->pauseNearPlayers=v);
        addIntSetting("Enemy pause range",2,24,1,()->enemyRange,v->enemyRange=v);
        addIntSetting("Pause below health",1,20,1,()->safeHealth,v->safeHealth=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null)return;
        if(mc.currentScreen!=null||mc.player.isHandActive()||unsafeToMend()||!hasMendingDamage()){restore();return;}
        if(cooldown>0){cooldown--;return;}
        int bottle=InventoryManager.findItem(mc,Items.EXPERIENCE_BOTTLE);
        if(bottle<0){restore();return;}
        if(originalSlot<0)originalSlot=mc.player.inventory.currentItem;
        int hotbar=bottle<9?bottle:freeHotbarSlot();
        if(bottle>=9){
            if(mc.player.openContainer!=mc.player.inventoryContainer||!InventoryManager.moveToHotbar(mc,bottle,hotbar))return;
            movedFrom=bottle;
        }
        bottleSlot=hotbar;
        mc.player.inventory.currentItem=hotbar;
        mc.playerController.updateController();
        ItemStack held=mc.player.getHeldItemMainhand();
        if(held.isEmpty()||held.getItem()!=Items.EXPERIENCE_BOTTLE)return;
        mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);
        cooldown=useDelay;
    }

    private boolean hasMendingDamage(){
        for(ItemStack stack:mc.player.getArmorInventoryList())if(needsRepair(stack))return true;
        return needsRepair(mc.player.getHeldItemMainhand())||needsRepair(mc.player.getHeldItemOffhand());
    }

    private boolean unsafeToMend(){
        if(mc.player.getHealth()<=safeHealth)return true;
        if(pauseNearPlayers==0)return false;
        double rangeSq=enemyRange*enemyRange;
        for(EntityPlayer other:mc.world.playerEntities)
            if(other!=mc.player&&!other.isDead&&!other.isSpectator()
                    &&!FriendManager.isFriend(other.getName())&&mc.player.getDistanceSq(other)<=rangeSq)return true;
        return false;
    }

    private int freeHotbarSlot(){
        for(int slot=0;slot<9;slot++)if(mc.player.inventory.getStackInSlot(slot).isEmpty())return slot;
        return originalSlot;
    }

    private boolean needsRepair(ItemStack stack){
        return stack!=null&&!stack.isEmpty()&&stack.isItemStackDamageable()&&stack.getItemDamage()>=minimumDamage&&
                EnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING,stack)>0;
    }

    private void restore(){
        if(mc.player!=null){
            if(movedFrom>=9&&bottleSlot>=0&&mc.playerController!=null
                    &&mc.player.openContainer==mc.player.inventoryContainer){
                ItemStack stack=mc.player.inventory.getStackInSlot(bottleSlot);
                if(stack.isEmpty()||stack.getItem()==Items.EXPERIENCE_BOTTLE)
                    mc.playerController.windowClick(mc.player.inventoryContainer.windowId,movedFrom,bottleSlot,
                            net.minecraft.inventory.ClickType.SWAP,mc.player);
            }
            if(restoreSlot!=0&&originalSlot>=0&&originalSlot<9&&mc.player.inventory.currentItem==bottleSlot)
                mc.player.inventory.currentItem=originalSlot;
        }
        originalSlot=bottleSlot=movedFrom=-1;cooldown=0;
    }

    @Override protected void onDisable(){restore();}
    @Override protected void onWorldUnload(){restore();}
}
