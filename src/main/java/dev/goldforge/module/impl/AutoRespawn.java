package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.GuiGameOver;

/** Respawns only while the vanilla death screen is active. */
public final class AutoRespawn extends Module {
    public int delayTicks=10;
    private int deathTicks;
    private boolean requested;

    public AutoRespawn(){
        super("AutoRespawn","Respawns after a configurable delay while the vanilla death screen is open.",Category.PLAYER,0);
        addIntSetting("Delay ticks",0,100,1,()->delayTicks,v->delayTicks=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||!(mc.currentScreen instanceof GuiGameOver)){
            deathTicks=0;requested=false;return;
        }
        if(requested)return;
        if(deathTicks<delayTicks){deathTicks++;return;}
        requested=true;
        mc.player.respawnPlayer();
        mc.displayGuiScreen(null);
        deathTicks=0;
    }

    @Override protected void onDisable(){deathTicks=0;requested=false;}
    @Override protected void onWorldUnload(){deathTicks=0;requested=false;}
}
