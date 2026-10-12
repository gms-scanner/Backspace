package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.InputUpdateEvent;

/** Holds forward movement using normal input and stops at a configured distance or obstruction. */
public final class AutoWalk extends Module {
    public int maxDistance=0,stopOnCollision=1;
    private double startX,startZ;
    private boolean tracking;

    public AutoWalk(){
        super("AutoWalk","Walks forward using normal movement input, with optional distance and collision stops.",Category.MOVEMENT,0);
        addIntSetting("Max distance (0=unlimited)",0,1000,10,()->maxDistance,v->maxDistance=v);
        addIntSetting("Stop on collision (0/1)",0,1,1,()->stopOnCollision,v->stopOnCollision=v);
    }

    @Override protected void onEnable(){
        if(mc.player==null){tracking=false;return;}
        startX=mc.player.posX;startZ=mc.player.posZ;tracking=true;
    }

    @Override public void tick(){
        EntityPlayerSP player=mc.player;
        if(player==null||mc.world==null){setEnabled(false);return;}
        if(mc.currentScreen!=null||player.isDead||player.isRiding()||player.isElytraFlying()||player.isInWater()||player.isInLava()){setEnabled(false);return;}
        if(stopOnCollision!=0&&player.collidedHorizontally){setEnabled(false);return;}
        if(maxDistance>0&&tracking){double dx=player.posX-startX,dz=player.posZ-startZ;if(dx*dx+dz*dz>=((double)maxDistance*maxDistance))setEnabled(false);}
    }

    @Override public void onInputUpdate(InputUpdateEvent event){
        if(mc.player==null||event.getEntityPlayer()!=mc.player||mc.currentScreen!=null)return;
        event.getMovementInput().moveForward=1F;
        event.getMovementInput().moveStrafe=0F;
    }

    @Override protected void onDisable(){tracking=false;}
    @Override protected void onWorldUnload(){tracking=false;}
}
