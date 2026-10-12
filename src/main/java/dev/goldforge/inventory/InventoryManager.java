package dev.goldforge.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemFood;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;

/** Shared inventory lookup and hotbar transfer helpers for client modules. */
public final class InventoryManager {
    private InventoryManager(){}

    /** Returns an inventory index in [0, 35], preferring the hotbar. */
    public static int findItem(Minecraft mc,Item item){
        if(mc==null||mc.player==null||item==null)return -1;
        for(int i=0;i<36;i++){ItemStack stack=mc.player.inventory.getStackInSlot(i);if(!stack.isEmpty()&&stack.getItem()==item)return i;}
        return -1;
    }

    public static int findItemInHotbar(Minecraft mc,Item item){
        if(mc==null||mc.player==null||item==null)return -1;
        for(int i=0;i<9;i++){ItemStack stack=mc.player.inventory.getStackInSlot(i);if(!stack.isEmpty()&&stack.getItem()==item)return i;}
        return -1;
    }

    public static int count(Minecraft mc,Item item){
        if(mc==null||mc.player==null||item==null)return 0;int total=0;
        for(int i=0;i<36;i++){ItemStack stack=mc.player.inventory.getStackInSlot(i);if(!stack.isEmpty()&&stack.getItem()==item)total+=stack.getCount();}
        return total;
    }

    /** Finds food across the full inventory, preferring the hotbar and then higher hunger value. */
    public static int findBestFood(Minecraft mc,boolean allowGoldenApple){
        if(mc==null||mc.player==null)return -1;
        int best=-1;float bestScore=Float.NEGATIVE_INFINITY;
        for(int i=0;i<36;i++){
            ItemStack stack=mc.player.inventory.getStackInSlot(i);if(stack.isEmpty())continue;
            Item item=stack.getItem();if(!(item instanceof ItemFood))continue;
            if(!allowGoldenApple&&item==net.minecraft.init.Items.GOLDEN_APPLE)continue;
            ItemFood food=(ItemFood)item;
            float score=food.getHealAmount(stack)*2F+food.getSaturationModifier(stack)*food.getHealAmount(stack)*2F;
            if(i<9)score+=.01F;
            if(score>bestScore){bestScore=score;best=i;}
        }
        return best;
    }

    /** Moves an inventory index to the requested hotbar slot with the standard SWAP click. */
    public static boolean moveToHotbar(Minecraft mc,int inventoryIndex,int hotbarSlot){
        if(mc==null||mc.player==null||mc.playerController==null||inventoryIndex<0||inventoryIndex>=36||hotbarSlot<0||hotbarSlot>8)return false;
        if(inventoryIndex<9)return inventoryIndex==hotbarSlot;
        mc.playerController.windowClick(mc.player.inventoryContainer.windowId,inventoryIndex,hotbarSlot,ClickType.SWAP,mc.player);
        return true;
    }

    /** Scores hotbar tools by block break speed and Efficiency level for the requested state. */
    public static int bestHotbarTool(Minecraft mc,IBlockState state){
        if(mc==null||mc.player==null||state==null)return -1;int best=-1;float bestScore=0F;
        for(int slot=0;slot<9;slot++){
            ItemStack stack=mc.player.inventory.getStackInSlot(slot);if(stack.isEmpty())continue;
            float score=stack.getDestroySpeed(state);int efficiency=EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY,stack);
            if(score>1F&&efficiency>0)score+=efficiency*efficiency+1F;
            if(stack.isItemStackDamageable())score*=Math.max(.25F,1F-(stack.getItemDamage()/(float)stack.getMaxDamage())*.35F);
            if(score>bestScore){bestScore=score;best=slot;}
        }
        return best;
    }
}
