package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;

/** Conserves ordinary sprint exhaustion by using walking when food is low. */
public final class AntiHunger extends Module {
    public int foodThreshold=12,alwaysWalk=0;
    private boolean conserving;
    public AntiHunger(){
        super("AntiHunger","Walks instead of sprinting when food is low.",Category.MOVEMENT,0);
        addIntSetting("Food threshold",1,20,1,()->foodThreshold,v->foodThreshold=v);
        addIntSetting("Always conserve (0/1)",0,1,1,()->alwaysWalk,v->alwaysWalk=v);
    }
    @Override public void tick(){
        conserving=mc.player!=null&&mc.world!=null&&(alwaysWalk!=0
                ||mc.player.getFoodStats().getFoodLevel()<=foodThreshold);
        if(conserving&&mc.player.isSprinting())mc.player.setSprinting(false);
    }
    @Override public String hudTag(){return conserving?"[Walking]":"";}
    @Override protected void onDisable(){conserving=false;}
    @Override protected void onWorldUnload(){conserving=false;}
}
