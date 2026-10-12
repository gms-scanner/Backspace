package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.InputUpdateEvent;

/** Prevents grounded player input from walking beyond the edge of supporting blocks. */
public final class SafeWalk extends Module {
    public int edgeMargin=3;

    public SafeWalk(){
        super("SafeWalk","Reduces movement input near unsupported edges while grounded.",Category.MOVEMENT,0);
        addIntSetting("Edge margin (1-8)",1,8,1,()->edgeMargin,v->edgeMargin=v);
    }

    @Override public void onInputUpdate(InputUpdateEvent event){
        EntityPlayerSP player=mc.player;
        if(player==null||event.getEntityPlayer()!=player||mc.world==null||!player.onGround||player.isRiding())return;
        net.minecraft.util.MovementInput input=event.getMovementInput();
        float originalForward=input.moveForward,originalStrafe=input.moveStrafe;
        if(originalForward==0F&&originalStrafe==0F)return;
        float forward=originalForward,strafe=originalStrafe;
        float margin=edgeMargin*.025F;
        while(forward!=0F&&!hasSupport(player,forward,strafe,margin))forward=towardZero(forward,.05F);
        while(strafe!=0F&&!hasSupport(player,forward,strafe,margin))strafe=towardZero(strafe,.05F);
        while(forward!=0F&&strafe!=0F&&!hasSupport(player,forward,strafe,margin)){forward=towardZero(forward,.05F);strafe=towardZero(strafe,.05F);}
        input.moveForward=forward;input.moveStrafe=strafe;
    }

    private boolean hasSupport(EntityPlayerSP player,float forward,float strafe,float margin){
        double length=Math.sqrt(forward*forward+strafe*strafe);
        if(length<1.0E-4D)return true;
        double yaw=Math.toRadians(player.rotationYaw);
        double dx=(strafe*Math.cos(yaw)-forward*Math.sin(yaw))/length*margin;
        double dz=(forward*Math.cos(yaw)+strafe*Math.sin(yaw))/length*margin;
        AxisAlignedBB below=player.getEntityBoundingBox().offset(dx,-.08D,dz);
        return !mc.world.getCollisionBoxes(player,below).isEmpty();
    }

    private float towardZero(float value,float amount){return value>0F?Math.max(0F,value-amount):Math.min(0F,value+amount);}
}
