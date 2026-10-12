package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;

/** Raises local gamma and restores the user's previous value when disabled. */
public final class Fullbright extends Module {
    public int brightness=100;
    private float originalGamma,appliedGamma=Float.NaN;

    public Fullbright(){
        super("Fullbright","Raises local brightness and restores the prior gamma setting when disabled.",Category.RENDER,0);
        addIntSetting("Brightness percent",10,100,5,()->brightness,v->brightness=v);
    }

    @Override protected void onEnable(){
        if(mc.gameSettings==null)return;
        originalGamma=mc.gameSettings.gammaSetting;appliedGamma=Float.NaN;
    }

    @Override public void tick(){
        if(mc.gameSettings==null)return;
        float desired=brightness/10F;
        float current=mc.gameSettings.gammaSetting;
        if(!Float.isNaN(appliedGamma)&&Math.abs(current-appliedGamma)>.001F)originalGamma=current;
        if(Math.abs(current-desired)>.001F)mc.gameSettings.gammaSetting=desired;
        appliedGamma=desired;
    }

    private void restore(){
        if(mc.gameSettings!=null&&!Float.isNaN(appliedGamma)&&Math.abs(mc.gameSettings.gammaSetting-appliedGamma)<.001F)mc.gameSettings.gammaSetting=originalGamma;
        appliedGamma=Float.NaN;
    }
    @Override protected void onDisable(){restore();}
    @Override protected void onWorldUnload(){restore();}
}
