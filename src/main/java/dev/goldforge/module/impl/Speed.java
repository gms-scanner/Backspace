package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.module.MovementManager;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Configurable strafe movement boost with optional automatic hops. */
public final class Speed extends Module {
    private static final double STRICT_BASE_SPEED=.35D,STRICT_STRAFE_BONUS=.02D,STRICT_MAX_SPEED=.37D;
    private static final double STRICT_GRAVITY=.08D,STRICT_DRAG=.98D,STRICT_TERMINAL=3.92D;
    public int mode=0,strength=8,autoJump=0,airControl=7,ncpAirSneak=0;
    public int strictRecoveryTicks=40,strictJumpInterval=20;
    private boolean forcedSneak;
    private static volatile Speed activeInstance;
    private int strictTickCounter,strictSpikeTicks,strictRiseTick;
    private boolean strictBufferCooldown,strictRising;
    private double strictMotionY;

    public Speed(){
        super("Speed","NCP-strict strafe profile with separate ground, hop, and NCP steering modes.",Category.MOVEMENT,0);
        addIntSetting("Mode (0 NCP Strict, 1 Ground, 2 Hop, 3 NCP)",0,3,1,()->mode,v->mode=v);
        addIntSetting("Steering response",1,10,1,()->strength,v->strength=v);
        addIntSetting("Auto jump (0/1)",0,1,1,()->autoJump,v->autoJump=v);
        addIntSetting("Air control",0,10,1,()->airControl,v->airControl=v);
        addIntSetting("NCP air sneak (0/1)",0,1,1,()->ncpAirSneak,v->ncpAirSneak=v);
        addIntSetting("Strict recovery ticks",10,80,5,()->strictRecoveryTicks,v->strictRecoveryTicks=v);
        addIntSetting("Strict jump interval",5,40,1,()->strictJumpInterval,v->strictJumpInterval=v);
    }

    @Override protected void onEnable(){activeInstance=this;resetStrictState();}
    @Override public String hudTag(){return "["+(mode==1?"Ground":mode==2?"Hop":mode==3?"NCP":"NCP Strict")+"]";}

    @Override public void tick(){
        EntityPlayerSP player=mc.player;
        if(player==null||mc.world==null){releaseForcedSneak();return;}
        updateForcedSneak(player);
        if(player.isRiding()||player.isElytraFlying()||player.isInWater()||player.isInLava()||(player.isSneaking()&&!forcedSneak))return;
        if(mc.world.getBlockState(new net.minecraft.util.math.BlockPos(player.posX,player.getEntityBoundingBox().minY,player.posZ)).getBlock()==Blocks.WEB)return;
        if(mode==0){runStrictProfile(player);return;}
        if(mode==1&&!player.onGround)return;
        float forward=player.movementInput.moveForward;
        float strafe=player.movementInput.moveStrafe;
        if(forward==0F&&strafe==0F)return;

        if(mode!=3&&(autoJump!=0||mode==2)&&player.onGround&&player.collidedVertically)player.jump();

        double inputLength=Math.sqrt(forward*forward+strafe*strafe);
        if(inputLength<1.0E-4D)return;
        forward/=inputLength;strafe/=inputLength;
        double yaw=Math.toRadians(player.rotationYaw);
        double x=strafe*Math.cos(yaw)-forward*Math.sin(yaw);
        double z=forward*Math.cos(yaw)+strafe*Math.sin(yaw);

        // Keep the module within the player's ordinary walk/sprint envelope.
        // Strength and air control tune acceleration/steering, not max speed.
        double potionMultiplier=1D;
        if(player.isPotionActive(Potion.getPotionById(1)))potionMultiplier+=.20D*(player.getActivePotionEffect(Potion.getPotionById(1)).getAmplifier()+1);
        double cap=(player.isSprinting()?.2873D:.221D)*potionMultiplier;
        double acceleration=player.onGround?.025D+strength*.0005D:.008D+airControl*.0009D+strength*.0005D;
        steerWithinEnvelope(player,x,z,cap,acceleration);
    }

    /** Preserves momentum and limits direction changes across ground and air movement. */
    private void steerWithinEnvelope(EntityPlayerSP player,double wishX,double wishZ,double cap,double accel){
        double targetX=wishX*cap,targetZ=wishZ*cap;
        double dx=targetX-player.motionX,dz=targetZ-player.motionZ;
        double delta=Math.sqrt(dx*dx+dz*dz);
        if(delta>accel&&delta>1.0E-6D){double scale=accel/delta;dx*=scale;dz*=scale;}
        double vx=player.motionX+dx,vz=player.motionZ+dz;

        // Limit the vector change so rapid W/A/S/D direction changes do not
        // instantly rotate the full horizontal velocity in one tick.
        double speed=Math.sqrt(vx*vx+vz*vz);
        if(speed>cap&&speed>1.0E-6D){double scale=cap/speed;vx*=scale;vz*=scale;}
        MovementManager.requestHorizontalVelocity(this,vx,vz,10);
    }

    private void updateForcedSneak(EntityPlayerSP player){
        boolean shouldForce=mode==3&&ncpAirSneak!=0&&!player.onGround&&(player.motionY<=0D||player.fallDistance>0F);
        if(shouldForce){KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),true);forcedSneak=true;}
        else releaseForcedSneak();
    }

    private void releaseForcedSneak(){
        if(!forcedSneak||mc.gameSettings==null)return;
        int key=mc.gameSettings.keyBindSneak.getKeyCode();
        KeyBinding.setKeyBindState(key,key>=0&&Keyboard.isKeyDown(key));
        forcedSneak=false;
    }

    /** Replace the former strafe mode with the supplied periodic NCP profile. */
    private void runStrictProfile(EntityPlayerSP player){
        strictTickCounter++;
        if(strictBufferCooldown){
            applyStrictHorizontal(player,STRICT_BASE_SPEED);
            applyStrictVertical(player);
            if(strictTickCounter%Math.max(1,strictRecoveryTicks)==0)strictBufferCooldown=false;
            return;
        }
        if(player.onGround&&strictTickCounter%Math.max(1,strictJumpInterval)==0){
            player.jump();strictSpikeTicks=1;strictRising=true;strictRiseTick=0;strictMotionY=.42D;
        }
        if(strictSpikeTicks>0){
            applyStrictHorizontal(player,STRICT_BASE_SPEED+STRICT_STRAFE_BONUS);
            strictSpikeTicks--;strictBufferCooldown=true;
        }else applyStrictHorizontal(player,STRICT_BASE_SPEED);
        applyStrictVertical(player);
    }

    private void applyStrictHorizontal(EntityPlayerSP player,double speed){
        float forward=player.movementInput==null?0F:player.movementInput.moveForward;
        float strafe=player.movementInput==null?0F:player.movementInput.moveStrafe;
        float yaw=player.rotationYaw;
        if(forward!=0F&&strafe!=0F){yaw+=(forward>0F?-45F:45F);strafe=0F;}
        double radians=Math.toRadians(yaw);
        double dx=-Math.sin(radians)*speed*forward+Math.cos(radians)*speed*strafe;
        double dz=Math.cos(radians)*speed*forward+Math.sin(radians)*speed*strafe;
        double length=Math.sqrt(dx*dx+dz*dz);
        if(length>STRICT_MAX_SPEED){double scale=STRICT_MAX_SPEED/length;dx*=scale;dz*=scale;}
        MovementManager.requestHorizontalVelocity(this,dx,dz,10);
    }

    private void applyStrictVertical(EntityPlayerSP player){
        if(strictRising){strictMotionY=.42D;if(++strictRiseTick>=4){strictRising=false;strictRiseTick=0;}}
        else{strictMotionY=Math.max(-STRICT_TERMINAL,(strictMotionY-STRICT_GRAVITY)*STRICT_DRAG);if(strictMotionY>-.001D&&player.onGround)strictMotionY=0D;}
        player.motionY=strictMotionY;
    }

    /** Called on the client thread when the packet observer sees a server position correction. */
    public static void onServerCorrection(){Speed speed=activeInstance;if(speed!=null&&speed.enabled)speed.resetStrictState();}

    private void resetStrictState(){strictTickCounter=0;strictSpikeTicks=0;strictBufferCooldown=false;strictMotionY=0D;strictRising=false;strictRiseTick=0;}

    @Override protected void onDisable(){releaseForcedSneak();resetStrictState();if(activeInstance==this)activeInstance=null;}
}
