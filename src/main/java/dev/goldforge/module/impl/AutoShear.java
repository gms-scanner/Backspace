package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.init.Items;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;

/** Shears an unsheared sheep when it is directly targeted and within normal interaction reach. */
public final class AutoShear extends Module {
    public int delayTicks=6,autoSelect=1;
    private int cooldown;
    private int originalSlot=-1;

    public AutoShear(){
        super("AutoShear","Shears a targeted sheep using normal entity interaction.",Category.PLAYER,0);
        addIntSetting("Action delay (ticks)",2,30,1,()->delayTicks,v->delayTicks=v);
        addIntSetting("Select shears (0/1)",0,1,1,()->autoSelect,v->autoSelect=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null){restore();return;}
        if(cooldown>0){cooldown--;return;}
        RayTraceResult hit=mc.objectMouseOver;
        if(hit==null||hit.typeOfHit!=RayTraceResult.Type.ENTITY||!(hit.entityHit instanceof EntitySheep))return;
        EntitySheep sheep=(EntitySheep)hit.entityHit;
        if(sheep.isDead||sheep.getSheared()||mc.player.getDistance(sheep)>3.0F)return;
        if(mc.player.getHeldItemMainhand().getItem()!=Items.SHEARS){
            if(autoSelect==0)return;
            int slot=InventoryManager.findItem(mc,Items.SHEARS);
            if(slot<0)return;
            if(originalSlot<0)originalSlot=mc.player.inventory.currentItem;
            int hotbar=slot<9?slot:originalSlot;
            if(slot>=9&&!InventoryManager.moveToHotbar(mc,slot,hotbar))return;
            mc.player.inventory.currentItem=hotbar;
        }
        if(mc.player.getHeldItemMainhand().getItem()!=Items.SHEARS)return;
        if(mc.playerController.interactWithEntity(mc.player,sheep,EnumHand.MAIN_HAND)!=net.minecraft.util.EnumActionResult.FAIL)cooldown=delayTicks;
    }

    private void restore(){if(originalSlot>=0&&mc.player!=null)mc.player.inventory.currentItem=originalSlot;originalSlot=-1;cooldown=0;}
    @Override protected void onDisable(){restore();}
    @Override protected void onWorldUnload(){restore();}
}
