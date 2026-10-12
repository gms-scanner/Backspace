package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

/** Places a torch on the selected face when its destination is dark and valid. */
public final class AutoTorch extends Module {
    public int delayTicks=8,maxLight=7,autoSelect=1;
    private int cooldown,originalSlot=-1;
    private BlockPos lastTarget;

    public AutoTorch(){
        super("AutoTorch","Lights a dark targeted spot with a normally placed torch.",Category.WORLD,0);
        addIntSetting("Action delay (ticks)",2,40,1,()->delayTicks,v->delayTicks=v);
        addIntSetting("Maximum light level",0,14,1,()->maxLight,v->maxLight=v);
        addIntSetting("Select torches (0/1)",0,1,1,()->autoSelect,v->autoSelect=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null){restore();lastTarget=null;return;}
        if(cooldown>0){cooldown--;return;}
        RayTraceResult hit=mc.objectMouseOver;
        if(hit==null||hit.typeOfHit!=RayTraceResult.Type.BLOCK){lastTarget=null;return;}
        BlockPos target=hit.getBlockPos().offset(hit.sideHit);
        if(target.equals(lastTarget)||!mc.world.isAirBlock(target)||mc.world.getLightFromNeighbors(target)>maxLight)return;
        if(!Blocks.TORCH.canPlaceBlockAt(mc.world,target))return;
        Item torch=Item.getItemFromBlock(Blocks.TORCH);
        if(mc.player.getHeldItemMainhand().getItem()!=torch){
            if(autoSelect==0)return;
            int slot=InventoryManager.findItem(mc,torch);
            if(slot<0)return;
            if(originalSlot<0)originalSlot=mc.player.inventory.currentItem;
            int hotbar=slot<9?slot:originalSlot;
            if(slot>=9&&!InventoryManager.moveToHotbar(mc,slot,hotbar))return;
            mc.player.inventory.currentItem=hotbar;
        }
        ItemStack stack=mc.player.getHeldItemMainhand();
        if(stack.isEmpty()||stack.getItem()!=torch)return;
        net.minecraft.util.EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,hit.getBlockPos(),hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND);
        if(result!=net.minecraft.util.EnumActionResult.FAIL){lastTarget=target.toImmutable();cooldown=delayTicks;}
    }

    private void restore(){if(originalSlot>=0&&mc.player!=null)mc.player.inventory.currentItem=originalSlot;originalSlot=-1;cooldown=0;}
    @Override protected void onDisable(){restore();lastTarget=null;}
    @Override protected void onWorldUnload(){restore();lastTarget=null;}
}
