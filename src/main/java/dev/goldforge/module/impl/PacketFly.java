package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;

/**
 * Experimental packet-driven flight profile for 1.12.2. Each client tick
 * resolves input into a target position, advances the local player, then sends
 * a small vertical pulse followed by the target position.
 */
public final class PacketFly extends Module {
    private int lastAntiKickTick = Integer.MIN_VALUE;

    /** Horizontal blocks per tick, scaled by 100 for the integer setting UI. */
    public int horizontalSpeed = 100;
    /** Vertical blocks per tick, scaled by 100 for the integer setting UI. */
    public int verticalSpeed = 50;
    /** Packet pulse size, scaled by 10000. */
    public int packetOffset = 625;
    /** Integer-backed toggle for CPacketPlayer.onGround. */
    public int spoofGround = 0;
    /** Periodic downward tap interval; zero disables the anti-kick profile. */
    public int antiKickPeriod = 20;

    public PacketFly() {
        super("PacketFly", "Experimental packet-driven flight profile.", Category.MOVEMENT, 0);
        addIntSetting("Horizontal speed (x100)", 5, 400, 5,
                () -> horizontalSpeed, v -> horizontalSpeed = v);
        addIntSetting("Vertical speed (x100)", 5, 200, 5,
                () -> verticalSpeed, v -> verticalSpeed = v);
        addIntSetting("Packet pulse (x10000)", 0, 2000, 25,
                () -> packetOffset, v -> packetOffset = v);
        addIntSetting("Spoof onGround (0/1)", 0, 1, 1,
                () -> spoofGround, v -> spoofGround = v);
        addIntSetting("Anti-kick interval (ticks, 0=off)", 0, 40, 1,
                () -> antiKickPeriod, v -> antiKickPeriod = v);
    }

    @Override
    protected void onEnable() {
        lastAntiKickTick = Integer.MIN_VALUE;
    }

    @Override
    protected void onDisable() {
        lastAntiKickTick = Integer.MIN_VALUE;
    }

    @Override
    public void tick() {
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null || mc.getConnection() == null || player.movementInput == null
                || player.isElytraFlying()) return;

        double forward = player.movementInput.moveForward;
        double strafe = player.movementInput.moveStrafe;
        double magnitude = Math.sqrt(forward * forward + strafe * strafe);
        if (magnitude > 1.0D) {
            forward /= magnitude;
            strafe /= magnitude;
        }

        double yaw = Math.toRadians(player.rotationYaw);
        double speed = clamp(horizontalSpeed, 5, 400) / 100.0D;
        double dx = (strafe * Math.cos(yaw) - forward * Math.sin(yaw)) * speed;
        double dz = (forward * Math.cos(yaw) + strafe * Math.sin(yaw)) * speed;
        double vertical = 0.0D;
        double verticalSpeedBlocks = clamp(verticalSpeed, 5, 200) / 100.0D;
        if (player.movementInput.jump) vertical = verticalSpeedBlocks;
        else if (player.movementInput.sneak) vertical = -verticalSpeedBlocks;

        int period = clamp(antiKickPeriod, 0, 40);
        if (period > 0 && player.ticksExisted % period == 0 && player.ticksExisted != lastAntiKickTick) {
            vertical = Math.min(vertical, -0.04D);
            lastAntiKickTick = player.ticksExisted;
        }

        double targetX = player.posX + dx;
        double targetY = player.posY + vertical;
        double targetZ = player.posZ + dz;
        boolean onGround = spoofGround != 0;
        double pulse = clamp(packetOffset, 0, 2000) / 10000.0D;

        player.setPosition(targetX, targetY, targetZ);
        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        player.fallDistance = 0.0F;

        if (pulse > 0.0D) {
            PacketManager.send(new CPacketPlayer.Position(targetX, targetY + pulse, targetZ, onGround), name);
        }
        PacketManager.send(new CPacketPlayer.Position(targetX, targetY, targetZ, onGround), name);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
