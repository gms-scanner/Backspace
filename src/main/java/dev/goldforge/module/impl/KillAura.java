package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;

/** Attacks the nearest eligible nearby target using the normal client attack controller. */
public final class KillAura extends Module {
    public int range=3,attackDelay=0,targets=0,rotate=0;
    /** 0: honor modern attack cooldown, 1: legacy 1.8-style cadence. */
    public int combatMode=0,attacksPerSecond=10;
    private int delayTicks;
    private long nextLegacyAttackNanos;

    public KillAura(){
        super("KillAura","Attacks the visible crosshair target; supports modern and legacy 1.8 attack timing.",Category.COMBAT,0);
        addIntSetting("Combat model (0 Modern, 1 Legacy 1.8)",0,1,1,()->combatMode,v->combatMode=v);
        addIntSetting("Legacy attack rate (CPS)",1,10,1,()->attacksPerSecond,v->attacksPerSecond=v);
        addIntSetting("Vanilla attack range",2,3,1,()->range,v->range=v);
        addIntSetting("Extra delay (ticks)",0,10,1,()->attackDelay,v->attackDelay=v);
        addIntSetting("Targets (0 players, 1 hostiles too)",0,1,1,()->targets,v->targets=v);
        addIntSetting("Camera aim assistance (0/1)",0,1,1,()->rotate,v->rotate=v);
    }

    @Override public String hudTag(){return combatMode==1?"[1.8 "+clampCps(attacksPerSecond)+" CPS]":"[Modern]";}

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null)return;
        if(delayTicks>0){delayTicks--;return;}
        EntityLivingBase target=selectTarget();
        if(target==null)return;
        if(rotate!=0)face(target);
        // Do not attack a nearby entity merely because it is visible. The
        // server validates the attack against the player's actual look ray.
        if(!isCrosshairTarget(target))return;
        long now=System.nanoTime();
        if(combatMode==0){
            if(mc.player.getCooledAttackStrength(0.5F)<1.0F)return;
        }else if(now<nextLegacyAttackNanos)return;
        dev.goldforge.module.Module criticalModule=dev.goldforge.GoldForge.MODULES.get("Criticals");
        Criticals criticals=criticalModule instanceof Criticals&&criticalModule.enabled?(Criticals)criticalModule:null;
        if(criticals!=null&&!criticals.readyToAttack(target))return;
        mc.playerController.attackEntity(mc.player,target);
        mc.player.swingArm(EnumHand.MAIN_HAND);
        if(criticals!=null)criticals.attackCompleted();
        delayTicks=attackDelay;
        if(combatMode==1)nextLegacyAttackNanos=now+1_000_000_000L/clampCps(attacksPerSecond);
    }

    private boolean isCrosshairTarget(EntityLivingBase target){
        return mc.objectMouseOver!=null
                &&mc.objectMouseOver.typeOfHit==net.minecraft.util.math.RayTraceResult.Type.ENTITY
                &&mc.objectMouseOver.entityHit==target
                &&mc.player.getDistanceSq(target)<=9.0D;
    }

    private EntityLivingBase selectTarget(){
        EntityLivingBase best=null;
        double allowedRange=Math.max(2,Math.min(3,range));
        double bestDistance=allowedRange*allowedRange;
        for(Entity entity:mc.world.loadedEntityList){
            if(!(entity instanceof EntityLivingBase)||entity==mc.player||entity.isDead)continue;
            EntityLivingBase living=(EntityLivingBase)entity;
            if(living.getHealth()<=0F||!isTarget(living))continue;
            if(living instanceof EntityPlayer&&FriendManager.isFriend(living.getName()))continue;
            double distance=mc.player.getDistanceSq(living);
            if(distance>bestDistance)continue;
            if(!mc.player.canEntityBeSeen(living))continue;
            if(best==null||distance<bestDistance){best=living;bestDistance=distance;}
        }
        return best;
    }

    private boolean isTarget(EntityLivingBase entity){
        if(entity instanceof EntityPlayer)return true;
        return targets!=0&&entity instanceof IMob;
    }

    private void face(EntityLivingBase target){
        double dx=target.posX-mc.player.posX;
        double dz=target.posZ-mc.player.posZ;
        double dy=target.posY+target.getEyeHeight()*.82D-(mc.player.posY+mc.player.getEyeHeight());
        double horizontal=Math.sqrt(dx*dx+dz*dz);
        float wantedYaw=(float)(Math.atan2(dz,dx)*180D/Math.PI)-90F;
        float wantedPitch=(float)(-Math.atan2(dy,horizontal)*180D/Math.PI);
        mc.player.rotationYaw+=MathHelper.clamp(MathHelper.wrapDegrees(wantedYaw-mc.player.rotationYaw),-35F,35F);
        mc.player.rotationPitch=MathHelper.clamp(mc.player.rotationPitch+MathHelper.clamp(MathHelper.wrapDegrees(wantedPitch-mc.player.rotationPitch),-25F,25F),-90F,90F);
    }

    private static int clampCps(int value){return Math.max(1,Math.min(10,value));}

    @Override protected void onDisable(){delayTicks=0;nextLegacyAttackNanos=0L;}
}
