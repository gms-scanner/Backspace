package dev.goldforge.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/** Read-only client movement state for diagnostics; it does not change player motion. */
public final class MovementSnapshot {
    public final double horizontalSpeed,verticalSpeed,x,y,z;
    public final float yaw,pitch,forward,strafe;
    public final boolean onGround,jumping,sprinting,collided;

    private MovementSnapshot(EntityPlayerSP player){
        x=player.posX;y=player.posY;z=player.posZ;
        horizontalSpeed=Math.sqrt(player.motionX*player.motionX+player.motionZ*player.motionZ);
        verticalSpeed=player.motionY;yaw=player.rotationYaw;pitch=player.rotationPitch;
        forward=player.movementInput==null?0:player.movementInput.moveForward;
        strafe=player.movementInput==null?0:player.movementInput.moveStrafe;
        onGround=player.onGround;jumping=player.movementInput!=null&&player.movementInput.jump;
        sprinting=player.isSprinting();collided=player.collidedHorizontally;
    }

    public static MovementSnapshot capture(){
        EntityPlayerSP player=Minecraft.getMinecraft().player;
        return player==null?null:new MovementSnapshot(player);
    }

    public double accelerationFrom(MovementSnapshot previous){return previous==null?0D:horizontalSpeed-previous.horizontalSpeed;}
    public float yawDeltaFrom(MovementSnapshot previous){
        if(previous==null)return 0F;
        float delta=(yaw-previous.yaw)%360F;
        if(delta>=180F)delta-=360F;
        if(delta< -180F)delta+=360F;
        return delta;
    }

    public String describe(){
        return String.format(java.util.Locale.ROOT,
                "pos %.2f %.2f %.2f | speed %.3f | vy %.3f | yaw %.1f pitch %.1f | input %.1f/%.1f | ground %s jump %s sprint %s collision %s",
                x,y,z,horizontalSpeed,verticalSpeed,yaw,pitch,forward,strafe,onGround,jumping,sprinting,collided);
    }
}
