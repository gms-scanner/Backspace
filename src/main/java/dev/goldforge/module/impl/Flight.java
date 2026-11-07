package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;

/** Vanilla-style flight controls with scoped restoration of local abilities. */
public final class Flight extends Module {
    public double speed=1.0;
    public boolean brake=true;
    private EntityPlayerSP controlled;
    private boolean oldAllowed,oldFlying;
    private float oldSpeed;

    public Flight(){
        super("Flight","Vanilla-style flight: WASD, Space up, Sneak down.",Category.MOVEMENT,0);
        addDoubleSetting("Speed multiplier",.2,5,.1,()->speed,v->speed=v);
        addIntSetting("Brake on release",0,1,1,()->brake?1:0,v->brake=v!=0);
    }
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.player.isDead||mc.player.isRiding()){
            restore();return;
        }
        if(controlled!=mc.player){
            restore();controlled=mc.player;
            oldAllowed=controlled.capabilities.allowFlying;
            oldFlying=controlled.capabilities.isFlying;
            oldSpeed=controlled.capabilities.getFlySpeed();
        }
        controlled.capabilities.allowFlying=true;
        controlled.capabilities.isFlying=true;
        controlled.capabilities.setFlySpeed((float)(.05*Math.max(.2,Math.min(5,speed))));
        if(isAutomationBlockedByScreen()){
            controlled.motionX=controlled.motionY=controlled.motionZ=0;
        }else if(brake&&controlled.movementInput!=null){
            if(controlled.movementInput.moveForward==0&&controlled.movementInput.moveStrafe==0)
                controlled.motionX=controlled.motionZ=0;
            if(!controlled.movementInput.jump&&!controlled.movementInput.sneak)controlled.motionY=0;
        }
    }
    private void restore(){
        if(controlled==null)return;
        controlled.capabilities.allowFlying=oldAllowed;
        controlled.capabilities.isFlying=oldFlying;
        controlled.capabilities.setFlySpeed(oldSpeed);
        controlled=null;
    }
    @Override protected void onDisable(){restore();}
    @Override public String hudTag(){return "Vanilla";}
}
