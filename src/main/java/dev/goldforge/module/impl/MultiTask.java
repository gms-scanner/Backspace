package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;

/** Sends a bounded use action while the ordinary controller is mining. */
public final class MultiTask extends Module {
    public int useInterval=4,allowBlocks=0;
    private int ticks,uses;
    public MultiTask(){
        super("MultiTask","Allows selected item use while the block controller is mining.",Category.PLAYER,0);
        addIntSetting("Use interval (ticks)",2,20,1,()->useInterval,v->useInterval=v);
        addIntSetting("Allow blocks (0/1)",0,1,1,()->allowBlocks,v->allowBlocks=v);
    }
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null)return;
        ticks++;
        if(ticks%Math.max(2,useInterval)!=0||!mc.playerController.getIsHittingBlock()
                ||!mc.gameSettings.keyBindUseItem.isKeyDown()||mc.player.isHandActive())return;
        ItemStack held=mc.player.getHeldItemMainhand();
        if(held.isEmpty())return;
        boolean consumable=held.getItem() instanceof ItemFood||held.getItem() instanceof ItemPotion;
        if(!consumable&&!(allowBlocks!=0&&held.getItem() instanceof ItemBlock))return;
        EnumActionResult result;
        if(held.getItem() instanceof ItemBlock){
            RayTraceResult hit=mc.objectMouseOver;
            if(hit==null||hit.typeOfHit!=RayTraceResult.Type.BLOCK)return;
            result=mc.playerController.processRightClickBlock(mc.player,mc.world,hit.getBlockPos(),
                    hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND);
        }else result=mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);
        if(result==EnumActionResult.SUCCESS)uses++;
    }
    @Override public String hudTag(){return "["+uses+" uses]";}
    @Override protected void onDisable(){ticks=uses=0;}
}
