package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.player.EntityPlayer;

/** Client-side Backspace cape, rendered only on the local player. */
public final class Cape extends Module {
    public Cape() {
        super("Cape", "Shows a yellow Backspace cape on your own player model.", Category.RENDER, 0);
    }

    public static boolean isEnabledFor(EntityPlayer player) {
        if (player == null || player != net.minecraft.client.Minecraft.getMinecraft().player) return false;
        dev.goldforge.module.Module module = dev.goldforge.GoldForge.MODULES.get("Cape");
        return module instanceof Cape && module.enabled && !module.isSuspended()
                && net.minecraft.client.Minecraft.getMinecraft().gameSettings.thirdPersonView != 0;
    }
}
