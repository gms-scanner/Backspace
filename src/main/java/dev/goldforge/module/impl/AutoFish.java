package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/** Reels on a bite and recasts with a fishing rod through the normal item-use path. */
public final class AutoFish extends Module {
    public int recastDelay=8,restoreSlot=1;
    private static final Field CATCHABLE_TICKS=findCatchableField();
    private int cooldown,originalSlot=-1;

    public AutoFish(){
        super("AutoFish","Reels in caught fish and recasts using ordinary fishing-rod interactions.",Category.PLAYER,0);
        addIntSetting("Recast delay (ticks)",2,40,1,()->recastDelay,v->recastDelay=v);
        addIntSetting("Restore previous slot (0/1)",0,1,1,()->restoreSlot,v->restoreSlot=v);
    }

    @Override public void tick(){
        EntityPlayerSP player=mc.player;
        if(player==null||mc.world==null||mc.playerController==null)return;
        if(mc.currentScreen!=null){restore();return;}
        if(cooldown>0){cooldown--;return;}
        if(player.getHeldItemMainhand().getItem()!=Items.FISHING_ROD){
            int rod=InventoryManager.findItem(mc,Items.FISHING_ROD);
            if(rod<0){restore();return;}
            if(originalSlot<0)originalSlot=player.inventory.currentItem;
            int hotbar=rod<9?rod:originalSlot;
            if(rod>=9&&!InventoryManager.moveToHotbar(mc,rod,hotbar))return;
            player.inventory.currentItem=hotbar;
        }
        EntityFishHook hook=player.fishEntity;
        if(hook==null||hook.isDead){
            mc.playerController.processRightClick(player,mc.world,EnumHand.MAIN_HAND);
            cooldown=recastDelay;
            return;
        }
        if(hook.caughtEntity!=null||catchableTicks(hook)>0){
            mc.playerController.processRightClick(player,mc.world,EnumHand.MAIN_HAND);
            cooldown=Math.max(3,recastDelay);
        }
    }

    private static int catchableTicks(EntityFishHook hook){
        if(CATCHABLE_TICKS==null)return 0;
        try{return CATCHABLE_TICKS.getInt(hook);}catch(IllegalAccessException ignored){return 0;}
    }

    private static Field findCatchableField(){
        try{return ReflectionHelper.findField(EntityFishHook.class,"ticksCatchable","field_146045_ax");}
        catch(Exception ignored){return null;}
    }

    private void restore(){
        if(restoreSlot!=0&&originalSlot>=0&&mc.player!=null&&originalSlot<9)mc.player.inventory.currentItem=originalSlot;
        originalSlot=-1;cooldown=0;
    }

    @Override protected void onDisable(){restore();}
    @Override protected void onWorldUnload(){restore();}
}
