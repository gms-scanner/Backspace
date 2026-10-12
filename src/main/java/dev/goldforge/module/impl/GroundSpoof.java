package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.AxisAlignedBB;

/** Controls the onGround bit on outgoing movement packets. */
public final class GroundSpoof extends Module {
    /** 0: collision-proximity profile, 1: unconditional ground flag. */
    public int mode = 0;
    public int groundMarginMm = 30;
    private static volatile boolean spoofGround;
    private static volatile int activeMode;

    public GroundSpoof() {
        super("GroundSpoof", "Adjusts movement packet ground flags with an NCP-oriented collision margin.", Category.MOVEMENT, 0);
        addIntSetting("Mode (0 NCP Strict, 1 Always)", 0, 1, 1, () -> mode, v -> mode = v);
        addIntSetting("NCP ground margin (mm)", 5, 100, 5, () -> groundMarginMm, v -> groundMarginMm = v);
    }

    @Override public void tick() {
        EntityPlayerSP player = mc.player;
        activeMode = mode;
        if (player == null || mc.world == null || player.isDead || player.isRiding() || player.isElytraFlying()
                || player.isInWater() || player.isInLava() || player.isOnLadder()
                || player.capabilities.isFlying || player.noClip) {
            spoofGround = false;
            return;
        }

        if (mode != 0) {
            spoofGround = true;
            return;
        }

        double margin = Math.max(5, Math.min(100, groundMarginMm)) / 1000.0D;
        AxisAlignedBB groundProbe = player.getEntityBoundingBox().offset(0.0D, -margin, 0.0D);
        spoofGround = player.motionY <= 0.0D && !mc.world.getCollisionBoxes(player, groundProbe).isEmpty();
    }

    /** Apply after NoFall so both modules can contribute their requested ground bit. */
    public static CPacketPlayer rewrite(CPacketPlayer packet) {
        if (packet == null || !spoofGround || packet.isOnGround()) return packet;
        if (packet instanceof CPacketPlayer.PositionRotation)
            return new CPacketPlayer.PositionRotation(packet.getX(0.0D), packet.getY(0.0D), packet.getZ(0.0D),
                    packet.getYaw(0.0F), packet.getPitch(0.0F), true);
        if (packet instanceof CPacketPlayer.Position)
            return new CPacketPlayer.Position(packet.getX(0.0D), packet.getY(0.0D), packet.getZ(0.0D), true);
        if (packet instanceof CPacketPlayer.Rotation)
            return new CPacketPlayer.Rotation(packet.getYaw(0.0F), packet.getPitch(0.0F), true);
        return new CPacketPlayer(true);
    }

    @Override protected void onDisable() { spoofGround = false; }
    @Override protected void onWorldUnload() { spoofGround = false; }
    @Override public String hudTag() { return activeMode == 0 ? "[NCP Strict]" : "[Always]"; }
}
