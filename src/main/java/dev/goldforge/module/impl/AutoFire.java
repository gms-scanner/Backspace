package dev.goldforge.module.impl;

import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

/** Uses flint and steel on the flammable block currently under the crosshair. */
public final class AutoFire extends Module {
    public int delayTicks=8,onlyWhenHeld=1;
    private int cooldown;
    private BlockPos lastTarget;

    public AutoFire(){
        super("AutoFire","Ignites the selected flammable block with vanilla flint-and-steel use.",Category.WORLD,0);
        addIntSetting("Action delay (ticks)",2,40,1,()->delayTicks,v->delayTicks=v);
        addIntSetting("Require flint and steel held (0/1)",0,1,1,()->onlyWhenHeld,v->onlyWhenHeld=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null){lastTarget=null;return;}
        if(cooldown>0){cooldown--;return;}
        RayTraceResult hit=mc.objectMouseOver;
        if(hit==null||hit.typeOfHit!=RayTraceResult.Type.BLOCK){lastTarget=null;return;}
        BlockPos pos=hit.getBlockPos();
        if(pos.equals(lastTarget))return;
        IBlockState state=mc.world.getBlockState(pos);
        if(!state.getBlock().isFlammable(mc.world,pos,hit.sideHit))return;
        if(onlyWhenHeld!=0&&mc.player.getHeldItemMainhand().getItem()!=Items.FLINT_AND_STEEL)return;
        if(onlyWhenHeld==0){
            int slot=InventoryManager.findItem(mc,Items.FLINT_AND_STEEL);
            if(slot<0)return;
            if(slot>=9){int current=mc.player.inventory.currentItem;if(!InventoryManager.moveToHotbar(mc,slot,current))return;slot=current;}
            mc.player.inventory.currentItem=slot;
        }
        ItemStack flint=mc.player.getHeldItemMainhand();
        if(flint.isEmpty()||flint.getItem()!=Items.FLINT_AND_STEEL)return;
        net.minecraft.util.EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,pos,hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND);
        if(result!=net.minecraft.util.EnumActionResult.FAIL){lastTarget=pos.toImmutable();cooldown=delayTicks;}
    }

    @Override protected void onDisable(){cooldown=0;lastTarget=null;}
    @Override protected void onWorldUnload(){cooldown=0;lastTarget=null;}
}
