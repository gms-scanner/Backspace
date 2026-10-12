package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.init.MobEffects;
import net.minecraft.util.text.TextComponentString;

/** Detects levitation transitions and optionally counters upward motion locally. */
public final class AntiLevitate extends Module {
    public int mode=0,notify=1,upwardCapTenths=0;
    private boolean wasLevitation;
    public AntiLevitate(){
        super("AntiLevitate","Detects Levitation and can cap local upward motion while it is active.",Category.MOVEMENT,0);
        addIntSetting("Mode (0 notify, 1 cap)",0,1,1,()->mode,v->mode=v);
        addIntSetting("Notifications (0/1)",0,1,1,()->notify,v->notify=v);
        addIntSetting("Upward cap (tenths)",0,10,1,()->upwardCapTenths,v->upwardCapTenths=v);
    }
    @Override public void tick(){
        if(mc.player==null||mc.world==null){wasLevitation=false;return;}
        boolean active=mc.player.isPotionActive(MobEffects.LEVITATION);
        if(active&&!wasLevitation&&notify!=0)mc.player.sendMessage(new TextComponentString("[Backspace] Levitation detected"));
        if(!active&&wasLevitation&&notify!=0)mc.player.sendMessage(new TextComponentString("[Backspace] Levitation ended"));
        if(active&&mode==1){double cap=Math.max(0,Math.min(1,upwardCapTenths/10.0D));if(mc.player.motionY>cap)mc.player.motionY=cap;}
        wasLevitation=active;
    }
    @Override protected void onDisable(){wasLevitation=false;}
}
