package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;

/** Adjusts local collision step height and restores it on cleanup. NCP mode is an experiment against the server's configured step allowance. */
public final class Step extends Module {
    /** 0 uses the existing custom height; 1 matches NCP's 1.12-era default step allowance. */
    public int mode=0;
    public int heightHundredths=100;
    public int ncpHeightHundredths=100;
    private float originalStepHeight;
    private boolean captured;

    public Step(){
        super("Step","Adjusts local collision stepping, with a 1.12.2 NCP-compatible height mode.",Category.MOVEMENT,0);
        addIntSetting("Mode (0 custom, 1 NCP 1.12.2)",0,1,1,()->mode,v->mode=v);
        addIntSetting("Step height (cm)",60,120,5,()->heightHundredths,v->heightHundredths=v);
        addIntSetting("NCP step height (cm)",50,120,1,()->ncpHeightHundredths,v->ncpHeightHundredths=v);
    }

    @Override protected void onEnable(){capture();}

    @Override public void tick(){
        EntityPlayerSP player=mc.player;
        if(player==null||mc.world==null||player.isDead||player.isRiding()||player.isElytraFlying()||player.isInWater()||player.isInLava()){
            restore();return;
        }
        if(!captured)capture();
        int selectedHeight=mode==1?Math.min(120,Math.max(50,ncpHeightHundredths)):heightHundredths;
        player.stepHeight=selectedHeight/100F;
    }

    private void capture(){if(mc.player!=null&&!captured){originalStepHeight=mc.player.stepHeight;captured=true;}}
    private void restore(){if(captured&&mc.player!=null)mc.player.stepHeight=originalStepHeight;captured=false;}
    @Override protected void onDisable(){restore();}
    @Override protected void onWorldUnload(){restore();}
}
