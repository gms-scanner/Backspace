package dev.goldforge.module.impl;

import dev.goldforge.module.*;
import net.minecraft.util.math.BlockPos;

public final class AutoNuker extends Module {
    public AutoNuker() { super("AutoNuker", "Continuously mines the block under your crosshair.", Category.WORLD, 0); }
    @Override public void tick() {
        if (mc.player == null || mc.world == null || mc.objectMouseOver == null || mc.objectMouseOver.getBlockPos() == null) return;
        BlockPos pos=mc.objectMouseOver.getBlockPos();
        mc.playerController.onPlayerDamageBlock(pos, mc.objectMouseOver.sideHit); mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
    }
}

