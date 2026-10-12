package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.InputUpdateEvent;

/** Requests vanilla sprinting while forward movement and normal sprint conditions are met. */
public final class AutoSprint extends Module {
    public int omniDirection=0;
    private boolean enabledSprint;

    public AutoSprint(){
        super("AutoSprint","Starts vanilla sprinting while moving and eligible; optional omni-directional mode.",Category.MOVEMENT,0);
        addIntSetting("Omni-directional (0/1)",0,1,1,()->omniDirection,v->omniDirection=v);
    }

    @Override public void onInputUpdate(InputUpdateEvent event){
        EntityPlayerSP player=mc.player;
        if(player==null||event.getEntityPlayer()!=player){releaseOwnedSprint();return;}
        boolean hasInput=omniDirection!=0?(event.getMovementInput().moveForward!=0F||event.getMovementInput().moveStrafe!=0F):event.getMovementInput().moveForward>0.8F;
        boolean eligible=hasInput&&!player.isSneaking()&&!player.isRiding()&&!player.isElytraFlying()&&!player.isInWater()&&!player.isInLava()&&!player.isHandActive()&&!player.collidedHorizontally&&player.getFoodStats().getFoodLevel()>6&&!player.isPotionActive(net.minecraft.init.MobEffects.BLINDNESS);
        if(eligible){if(!player.isSprinting()){player.setSprinting(true);enabledSprint=true;}}
        else releaseOwnedSprint();
    }

    private void releaseOwnedSprint(){
        if(enabledSprint&&mc.player!=null&&!mc.gameSettings.keyBindSprint.isKeyDown())mc.player.setSprinting(false);
        enabledSprint=false;
    }

    @Override protected void onDisable(){releaseOwnedSprint();}
    @Override protected void onWorldUnload(){releaseOwnedSprint();}
}
