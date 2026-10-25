package dev.goldforge.module.impl;

import dev.goldforge.GoldForge;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.util.ScreenShotHelper;

/** Captures the visible client frame only for confirmed high-confidence observations. */
public final class AutoScreenshot extends Module {
    public int minConfidence=80,cooldownSeconds=30;
    private long lastCapture;
    public AutoScreenshot(){super("AutoScreenshot","Saves screenshots of high-confidence discoveries.",Category.CLIENT,0);
        addIntSetting("Minimum confidence",50,100,5,()->minConfidence,v->minConfidence=v);
        addIntSetting("Cooldown seconds",5,300,5,()->cooldownSeconds,v->cooldownSeconds=v);}
    public static void capture(int confidence){
        Module module=GoldForge.MODULES.get("AutoScreenshot");if(!(module instanceof AutoScreenshot)||!module.enabled)return;
        AutoScreenshot screenshot=(AutoScreenshot)module;
        long now=System.currentTimeMillis();
        if(confidence<screenshot.minConfidence||now-screenshot.lastCapture<screenshot.cooldownSeconds*1000L)return;
        if(screenshot.mc.world==null||screenshot.mc.player==null)return;
        screenshot.lastCapture=now;
        screenshot.mc.player.sendMessage(ScreenShotHelper.saveScreenshot(screenshot.mc.mcDataDir,screenshot.mc.displayWidth,screenshot.mc.displayHeight,screenshot.mc.getFramebuffer()));
    }
    @Override protected void onEnable(){lastCapture=0L;}
}
