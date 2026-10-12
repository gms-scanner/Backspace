package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;

/**
 * Adjusts the local response after the ordinary server velocity packet has
 * been applied. NCP Strict preserves the vertical impulse and only scales the
 * horizontal response; Custom also allows vertical scaling for experiments.
 */
public final class Velocity extends Module {
    /** 0: conservative NCP profile, 1: custom horizontal and vertical response. */
    public int mode = 0;
    public int horizontalPercent = 85;
    public int verticalPercent = 100;

    public Velocity() {
        super("Velocity", "Scales local knockback response; NCP Strict keeps a strong, predictable impulse.", Category.COMBAT, 0);
        addIntSetting("Mode (0 NCP Strict, 1 Custom)", 0, 1, 1, () -> mode, v -> {
            mode = v;
            if (mode == 0) horizontalPercent = ncpHorizontal(horizontalPercent);
        });
        addIntSetting("Horizontal response (%) (NCP min 80)", 0, 100, 5, () -> horizontalPercent,
                v -> horizontalPercent = mode == 0 ? ncpHorizontal(v) : clamp(v));
        addIntSetting("Vertical response (%) (Custom)", 0, 100, 5, () -> verticalPercent, v -> verticalPercent = v);
    }

    @Override public String hudTag() {
        return mode == 0 ? "[NCP " + ncpHorizontal(horizontalPercent) + "%]" : "[" + clamp(horizontalPercent) + "/" + clamp(verticalPercent) + "%]";
    }

    /** Snapshot the active profile on the client thread after vanilla handles the packet. */
    public static PendingResponse capture(int entityId) {
        Velocity module = getEnabledModule();
        if (module == null || module.mc.player == null || module.mc.player.getEntityId() != entityId) return null;
        return new PendingResponse(entityId, module.mode == 0 ? ncpHorizontal(module.horizontalPercent) : clamp(module.horizontalPercent),
                module.mode == 0 ? 100 : clamp(module.verticalPercent));
    }

    /** Called on the client thread after vanilla has applied SPacketEntityVelocity. */
    public static void apply(PendingResponse response) {
        if (response == null) return;
        net.minecraft.client.entity.EntityPlayerSP player = net.minecraft.client.Minecraft.getMinecraft().player;
        if (player == null || player.getEntityId() != response.entityId) return;
        double horizontal = response.horizontalPercent / 100.0D;
        double vertical = response.verticalPercent / 100.0D;
        player.motionX *= horizontal;
        player.motionZ *= horizontal;
        player.motionY *= vertical;
    }

    private static Velocity getEnabledModule() {
        dev.goldforge.module.Module module = dev.goldforge.GoldForge.MODULES.get("Velocity");
        return module instanceof Velocity && module.enabled && !module.isSuspended() ? (Velocity) module : null;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static int ncpHorizontal(int value) { return Math.max(80, clamp(value)); }

    public static final class PendingResponse {
        private final int entityId;
        private final int horizontalPercent;
        private final int verticalPercent;
        private PendingResponse(int entityId, int horizontalPercent, int verticalPercent) {
            this.entityId = entityId;
            this.horizontalPercent = horizontalPercent;
            this.verticalPercent = verticalPercent;
        }
    }
}
