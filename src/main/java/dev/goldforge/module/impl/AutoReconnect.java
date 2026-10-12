package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;

/** Reconnects to the current server after the vanilla disconnect screen has remained open. */
public final class AutoReconnect extends Module {
    public int delaySeconds=8,maxAttempts=5;
    private int waitTicks,attempts;

    public AutoReconnect(){
        super("AutoReconnect","Retries the current multiplayer server after a configurable disconnect delay.",Category.CLIENT,0);
        addIntSetting("Delay seconds",2,60,1,()->delaySeconds,v->delaySeconds=v);
        addIntSetting("Maximum attempts (0 = unlimited)",0,20,1,()->maxAttempts,v->maxAttempts=v);
    }

    @Override public void tick(){
        if(mc.world!=null){waitTicks=0;attempts=0;return;}
        if(!(mc.currentScreen instanceof GuiDisconnected)){waitTicks=0;return;}
        waitTicks++;
        if(waitTicks<delaySeconds*20)return;
        if(maxAttempts>0&&attempts>=maxAttempts)return;
        ServerData server=mc.getCurrentServerData();
        if(server==null||server.serverIP==null||server.serverIP.trim().isEmpty())return;
        attempts++;
        waitTicks=0;
        mc.displayGuiScreen(new GuiConnecting(mc.currentScreen,mc,server));
    }

    @Override protected void onDisable(){waitTicks=0;attempts=0;}
    @Override protected void onWorldUnload(){waitTicks=0;}
}
