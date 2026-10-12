package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.WorldClient;

/** Disconnects from a remote server once health falls to the configured threshold. */
public final class AutoLog extends Module {
    public int healthThreshold=6,includeAbsorption=0;

    public AutoLog(){
        super("AutoLog","Disconnects from a multiplayer server when health reaches the configured threshold.",Category.PLAYER,0);
        addIntSetting("Health threshold (hearts)",1,10,1,()->healthThreshold,v->healthThreshold=v);
        addIntSetting("Count absorption (0/1)",0,1,1,()->includeAbsorption,v->includeAbsorption=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.getCurrentServerData()==null||!mc.player.isEntityAlive())return;
        float effectiveHealth=mc.player.getHealth()+(includeAbsorption!=0?mc.player.getAbsorptionAmount():0F);
        if(effectiveHealth>healthThreshold*2F)return;
        setEnabled(false);
        mc.world.sendQuittingDisconnectingPacket();
        mc.loadWorld((WorldClient)null);
        mc.displayGuiScreen(new GuiMainMenu());
    }
}
