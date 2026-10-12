package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBow;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.MathHelper;

/** Leads a visible player with the low ballistic arc while the vanilla bow is drawn. */
public final class BowAssist extends Module {
    public int targetRange=48,maxAimAngle=55,minChargeTicks=8,friendFilter=1,projectileLead=1,silentAim=1;

    public BowAssist(){
        super("BowAssist","Leads a visible player using 1.12 bow charge and projectile gravity while drawing.",Category.COMBAT,0);
        addIntSetting("Target range",8,64,4,()->targetRange,v->targetRange=v);
        addIntSetting("Maximum aim angle",10,90,5,()->maxAimAngle,v->maxAimAngle=v);
        addIntSetting("Minimum draw ticks",1,20,1,()->minChargeTicks,v->minChargeTicks=v);
        addIntSetting("Ignore friends (0/1)",0,1,1,()->friendFilter,v->friendFilter=v);
        addIntSetting("Lead target motion (0/1)",0,1,1,()->projectileLead,v->projectileLead=v);
        addIntSetting("Silent aim (0/1)",0,1,1,()->silentAim,v->silentAim=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.getConnection()==null||mc.currentScreen!=null)return;
        if(mc.player.getHeldItemMainhand().getItem()!=Items.BOW||!mc.player.isHandActive()||mc.player.getActiveHand()!=net.minecraft.util.EnumHand.MAIN_HAND)return;
        int drawTicks=72000-mc.player.getItemInUseCount();
        if(drawTicks<minChargeTicks)return;
        float charge=ItemBow.getArrowVelocity(drawTicks);
        if(charge<=0.05F)return;
        EntityPlayer target=findTarget();if(target==null)return;
        float[] rotation=solveAim(target,charge);
        if(rotation==null)return;
        if(silentAim!=0)PacketManager.send(new CPacketPlayer.Rotation(rotation[0],rotation[1],mc.player.onGround),name);
        else {mc.player.rotationYaw=rotation[0];mc.player.rotationPitch=rotation[1];}
    }

    private EntityPlayer findTarget(){
        EntityPlayer best=null;double bestAngle=Double.MAX_VALUE;double range=targetRange;
        for(EntityPlayer player:mc.world.playerEntities){
            if(player==mc.player||player.isDead||player.getHealth()<=0F||player.isSpectator())continue;
            if(friendFilter!=0&&FriendManager.isFriend(player.getName()))continue;
            double distance=mc.player.getDistance(player);if(distance>range||!mc.player.canEntityBeSeen(player))continue;
            double dx=player.posX-mc.player.posX,dz=player.posZ-mc.player.posZ,dy=player.posY+player.height*.5D-(mc.player.posY+mc.player.getEyeHeight());
            float directYaw=(float)(Math.atan2(dz,dx)*180D/Math.PI)-90F;
            float directPitch=(float)(-Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))*180D/Math.PI);
            double angle=Math.hypot(MathHelper.wrapDegrees(directYaw-mc.player.rotationYaw),directPitch-mc.player.rotationPitch);
            if(angle>maxAimAngle||angle>=bestAngle)continue;
            best=player;bestAngle=angle;
        }
        return best;
    }

    private float[] solveAim(EntityPlayer target,float charge){
        double speed=charge*3.0D,gravity=.05D;
        double eyeY=mc.player.posY+mc.player.getEyeHeight();
        double targetX=target.posX,targetZ=target.posZ,targetY=target.posY+target.height*.45D;
        double dx=targetX-mc.player.posX,dz=targetZ-mc.player.posZ,dy=targetY-eyeY;
        double horizontal=Math.sqrt(dx*dx+dz*dz);
        if(projectileLead!=0&&horizontal>0.01D){
            double initialTime=horizontal/Math.max(.1D,speed*.9D);
            double lead=Math.min(initialTime,12D);
            targetX+=MathHelper.clamp(target.posX-target.lastTickPosX,-.8D,.8D)*lead;
            targetZ+=MathHelper.clamp(target.posZ-target.lastTickPosZ,-.8D,.8D)*lead;
            dx=targetX-mc.player.posX;dz=targetZ-mc.player.posZ;horizontal=Math.sqrt(dx*dx+dz*dz);
        }
        if(horizontal<.01D)return null;
        double v2=speed*speed;
        double discriminant=v2*v2-gravity*(gravity*horizontal*horizontal+2D*dy*v2);
        if(discriminant<0D)return null;
        double tangent=(v2-Math.sqrt(discriminant))/(gravity*horizontal);
        double elevation=Math.atan(tangent);
        float yaw=(float)(Math.atan2(dz,dx)*180D/Math.PI)-90F;
        float pitch=(float)(-elevation*180D/Math.PI);
        return new float[]{yaw,pitch};
    }
}
