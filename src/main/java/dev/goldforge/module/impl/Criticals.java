package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.EntityLivingBase;

/** Critical-hit timing helper used by KillAura. */
public final class Criticals extends Module {
    /** 0 normal attack, 1 mini jump, 2 normal jump. */
    public int mode=0;
    private int pendingTarget=-1,waitTicks;

    public Criticals(){
        super("Criticals","Optional mini-jump or normal-jump timing; disabled by default.",Category.COMBAT,0);
        addIntSetting("Mode (0 Off, 1 Mini jump, 2 Jump)",0,2,1,()->mode,v->mode=v);
    }

    /** Returns true only when the target can be attacked in the selected mode. */
    public boolean readyToAttack(EntityLivingBase target){
        if(mc.player==null||target==null)return false;
        if(mode==0){
            clearPending();
            return true;
        }
        if(pendingTarget!=target.getEntityId()){
            pendingTarget=target.getEntityId();waitTicks=0;
            if(mc.player.onGround){
                if(mode==1)mc.player.motionY=.10D;
                else mc.player.jump();
                return false;
            }
        }
        if(++waitTicks>25){clearPending();return false;}
        return !mc.player.onGround&&mc.player.fallDistance>.05F;
    }

    public void attackCompleted(){clearPending();}
    private void clearPending(){pendingTarget=-1;waitTicks=0;}
    @Override protected void onDisable(){clearPending();}
}
