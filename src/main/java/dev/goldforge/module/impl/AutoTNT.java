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

/** Performs one normal TNT placement or ignition action on the block under the crosshair. */
public final class AutoTNT extends Module {
    /** 0 places TNT against the selected support block; 1 ignites the selected TNT block. */
    public int mode=0,delayTicks=10,requireHeld=1;
    private int cooldown;
    private BlockPos lastTarget;

    public AutoTNT(){
        super("AutoTNT","Places or ignites the TNT block selected by your crosshair, with a bounded action delay.",Category.WORLD,0);
        addIntSetting("Mode (0 place, 1 ignite)",0,1,1,()->mode,v->mode=v);
        addIntSetting("Action delay (ticks)",2,40,1,()->delayTicks,v->delayTicks=v);
        addIntSetting("Require item held (0/1)",0,1,1,()->requireHeld,v->requireHeld=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null){lastTarget=null;return;}
        if(cooldown>0){cooldown--;return;}
        RayTraceResult hit=mc.objectMouseOver;
        if(hit==null||hit.typeOfHit!=RayTraceResult.Type.BLOCK){lastTarget=null;return;}
        BlockPos clicked=hit.getBlockPos();
        if(clicked.equals(lastTarget))return;
        if(mode==0)place(hit,clicked);
        else ignite(hit,clicked);
    }

    private void place(RayTraceResult hit,BlockPos support){
        if(mc.world.isAirBlock(support)||!mc.world.getBlockState(support).isSideSolid(mc.world,support,hit.sideHit))return;
        BlockPos target=support.offset(hit.sideHit);
        if(!mc.world.isAirBlock(target))return;
        Item tnt=Item.getItemFromBlock(Blocks.TNT);
        if(requireHeld!=0&&mc.player.getHeldItemMainhand().getItem()!=tnt)return;
        if(requireHeld==0&&mc.player.getHeldItemMainhand().getItem()!=tnt){
            int slot=InventoryManager.findItem(mc,tnt);if(slot<0)return;
            if(slot>=9){int current=mc.player.inventory.currentItem;if(!InventoryManager.moveToHotbar(mc,slot,current))return;slot=current;}
            mc.player.inventory.currentItem=slot;
        }
        if(mc.player.getHeldItemMainhand().getItem()!=tnt)return;
        interact(hit);
    }

    private void ignite(RayTraceResult hit,BlockPos target){
        if(mc.world.getBlockState(target).getBlock()!=Blocks.TNT)return;
        if(requireHeld!=0&&mc.player.getHeldItemMainhand().getItem()!=Items.FLINT_AND_STEEL)return;
        if(requireHeld==0&&mc.player.getHeldItemMainhand().getItem()!=Items.FLINT_AND_STEEL){
            int slot=InventoryManager.findItem(mc,Items.FLINT_AND_STEEL);if(slot<0)return;
            if(slot>=9){int current=mc.player.inventory.currentItem;if(!InventoryManager.moveToHotbar(mc,slot,current))return;slot=current;}
            mc.player.inventory.currentItem=slot;
        }
        if(mc.player.getHeldItemMainhand().getItem()!=Items.FLINT_AND_STEEL)return;
        interact(hit);
    }

    private void interact(RayTraceResult hit){
        net.minecraft.util.EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,hit.getBlockPos(),hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND);
        if(result!=net.minecraft.util.EnumActionResult.FAIL){lastTarget=hit.getBlockPos().toImmutable();cooldown=delayTicks;}
    }

    @Override protected void onDisable(){cooldown=0;lastTarget=null;}
    @Override protected void onWorldUnload(){cooldown=0;lastTarget=null;}
}
