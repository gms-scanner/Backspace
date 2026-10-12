package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;

/** Adjustable control and position-packet profiles for Elytra gliding. */
public final class ElytraFly extends Module {
    /** 0=Control, 1=Packet, 2=Control+Packet. */
    public int mode = 0;
    /** Target horizontal speed in blocks/tick, scaled by 100. */
    public int horizontalSpeed = 150;
    /** Horizontal steering acceleration, scaled by 1000. */
    public int steering = 30;
    /** Vertical input speed, scaled by 100. */
    public int verticalSpeed = 30;
    /** Packet-mode extra movement multiplier, scaled by 100. */
    public int packetMultiplier = 150;
    /** Vertical packet pulse, scaled by 10000. */
    public int packetPulse = 625;
    /** Integer-backed toggle for the movement packet onGround field. */
    public int spoofGround = 0;

    public ElytraFly() {
        super("ElytraFly", "Configurable Elytra steering and packet movement profiles.", Category.MOVEMENT, 0);
        addIntSetting("Mode (0 Control, 1 Packet, 2 Both)", 0, 2, 1, () -> mode, v -> mode = v);
        addIntSetting("Horizontal speed (x100)", 20, 500, 5, () -> horizontalSpeed, v -> horizontalSpeed = v);
        addIntSetting("Steering (x1000)", 1, 200, 1, () -> steering, v -> steering = v);
        addIntSetting("Vertical input speed (x100)", 5, 200, 5, () -> verticalSpeed, v -> verticalSpeed = v);
        addIntSetting("Packet multiplier (x100)", 100, 400, 5, () -> packetMultiplier, v -> packetMultiplier = v);
        addIntSetting("Packet pulse (x10000)", 0, 2000, 25, () -> packetPulse, v -> packetPulse = v);
        addIntSetting("Spoof onGround (0/1)", 0, 1, 1, () -> spoofGround, v -> spoofGround = v);
    }

    @Override
    public String hudTag() {
        return "[" + (mode == 1 ? "Packet" : mode == 2 ? "Both" : "Control") + "]";
    }

    @Override
    public void tick() {
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null || mc.getConnection() == null || !player.isElytraFlying()) return;

        int selectedMode = clamp(mode, 0, 2);
        if (selectedMode == 0 || selectedMode == 2) applyControl(player);
        if (selectedMode == 1 || selectedMode == 2) applyPacketProfile(player);
    }

    private void applyControl(EntityPlayerSP player) {
        if (player.movementInput == null) return;
        double forward = player.movementInput.moveForward;
        double strafe = player.movementInput.moveStrafe;
        double length = Math.sqrt(forward * forward + strafe * strafe);

        if (length > 1.0D) {
            forward /= length;
            strafe /= length;
        }
        if (length > 1.0E-4D) {
            double yaw = Math.toRadians(player.rotationYaw);
            double wishX = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
            double wishZ = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
            double targetSpeed = clamp(horizontalSpeed, 20, 500) / 100.0D;
            double acceleration = clamp(steering, 1, 200) / 1000.0D;
            double targetX = wishX * targetSpeed;
            double targetZ = wishZ * targetSpeed;
            double deltaX = targetX - player.motionX;
            double deltaZ = targetZ - player.motionZ;
            double deltaLength = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            if (deltaLength > acceleration && deltaLength > 1.0E-6D) {
                double scale = acceleration / deltaLength;
                deltaX *= scale;
                deltaZ *= scale;
            }
            player.motionX += deltaX;
            player.motionZ += deltaZ;
        }

        double vertical = clamp(verticalSpeed, 5, 200) / 100.0D;
        if (player.movementInput.jump) player.motionY = Math.max(player.motionY, vertical);
        else if (player.movementInput.sneak) player.motionY = Math.min(player.motionY, -vertical);
    }

    private void applyPacketProfile(EntityPlayerSP player) {
        double multiplier = clamp(packetMultiplier, 100, 400) / 100.0D;
        double extra = multiplier - 1.0D;
        if (extra > 0.0D) {
            player.setPosition(player.posX + player.motionX * extra,
                    player.posY + player.motionY * extra,
                    player.posZ + player.motionZ * extra);
        }

        boolean onGround = spoofGround != 0;
        double pulse = clamp(packetPulse, 0, 2000) / 10000.0D;
        if (pulse > 0.0D) {
            PacketManager.send(new CPacketPlayer.Position(player.posX, player.posY + pulse, player.posZ, onGround), name);
        }
        PacketManager.send(new CPacketPlayer.Position(player.posX, player.posY, player.posZ, onGround), name);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
