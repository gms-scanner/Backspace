package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.block.material.Material;

/** Water-surface movement with an NCP-oriented, non-flat hop profile. */
public final class Jesus extends Module {
    /** 0: periodic surface hops, 1: constant-height surface mode. */
    public int mode = 0;
    public int hopPeriod = 2;
    public int hopImpulseMilli = 80;
    public int requireMovement = 1;
    private int hopTicks;

    public Jesus() {
        super("Jesus", "Walks across shallow water; NCP mode uses periodic vertical surface hops.", Category.MOVEMENT, 0);
        addIntSetting("Mode (0 NCP Strict, 1 Surface)", 0, 1, 1, () -> mode, v -> mode = v);
        addIntSetting("NCP hop period (ticks)", 2, 6, 1, () -> hopPeriod, v -> hopPeriod = v);
        addIntSetting("NCP hop impulse (milli-blocks/tick)", 40, 140, 10, () -> hopImpulseMilli, v -> hopImpulseMilli = v);
        addIntSetting("Require movement input (0/1)", 0, 1, 1, () -> requireMovement, v -> requireMovement = v);
    }

    @Override public void tick() {
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null || player.isDead || player.isRiding() || player.isElytraFlying()
                || player.capabilities.isFlying || player.noClip || player.isOnLadder() || player.isInLava()
                || player.isSneaking() || !isShallowWaterSurface(mc.world, player)) {
            hopTicks = 0;
            return;
        }

        boolean hasInput = Math.abs(player.movementInput.moveForward) > 0.001F
                || Math.abs(player.movementInput.moveStrafe) > 0.001F;
        if (requireMovement != 0 && !hasInput) {
            hopTicks = 0;
            return;
        }

        if (mode == 1) {
            player.motionY = 0.0D;
            hopTicks = 0;
            return;
        }

        int period = MathHelper.clamp(hopPeriod, 2, 6);
        if (++hopTicks >= period && player.motionY < 0.10D) {
            player.motionY = Math.max(player.motionY, MathHelper.clamp(hopImpulseMilli, 40, 140) / 1000.0D);
            hopTicks = 0;
        }
    }

    private static boolean isShallowWaterSurface(World world, EntityPlayerSP player) {
        BlockPos feet = new BlockPos(player.posX, player.posY, player.posZ);
        IBlockState state = world.getBlockState(feet);
        if (state.getMaterial() != Material.WATER) return false;

        // Only engage at the top layer: avoid changing ordinary underwater swimming.
        if (world.getBlockState(feet.up()).getMaterial() == Material.WATER) return false;
        double fractionalY = player.posY - Math.floor(player.posY);
        return fractionalY >= 0.50D;
    }

    @Override protected void onDisable() { hopTicks = 0; }
    @Override protected void onWorldUnload() { hopTicks = 0; }
    @Override public String hudTag() { return mode == 0 ? "[NCP Strict]" : "[Surface]"; }
}
