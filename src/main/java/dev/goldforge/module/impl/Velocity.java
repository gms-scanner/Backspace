package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;

/**
 * Adjusts the local response after vanilla applies incoming knockback.  Both
 * entity-velocity and explosion packets are covered, so TNT/crystal knockback
 * does not silently bypass the module.
 */
public final class Velocity extends Module {
    /** 0: conservative horizontal-only profile, 1: custom three-axis profile. */
    public int mode = 1;
    /** Percentage of knockback removed, not the small amount still kept. */
    public int horizontalPercent = 90;
    public int verticalPercent = 90;
    public int explosions = 1;

    public Velocity() {
        super("Velocity", "Reduces entity and explosion knockback after vanilla applies it.", Category.COMBAT, 0);
        addIntSetting("Mode (0 NCP Safe, 1 Custom)", 0, 1, 1, () -> mode, v -> {
            mode = v;
            if (mode == 0) horizontalPercent = ncpReduction(horizontalPercent);
        });
        addIntSetting("Horizontal knockback reduction (%)", 0, 100, 5, () -> horizontalPercent,
                v -> horizontalPercent = mode == 0 ? ncpReduction(v) : clamp(v));
        addIntSetting("Vertical knockback reduction (%) (Custom)", 0, 100, 5, () -> verticalPercent, v -> verticalPercent = clamp(v));
        addIntSetting("Reduce explosion knockback", 0, 1, 1, () -> explosions, v -> explosions = v);
    }

    @Override public String hudTag() {
        return mode == 0 ? "[NCP -" + ncpReduction(horizontalPercent) + "%]"
                : "[-" + clamp(horizontalPercent) + "/-" + clamp(verticalPercent) + "%]";
    }

    /** Snapshot the active profile on the client thread after vanilla handles the packet. */
    public static PendingResponse capture(int entityId) {
        Velocity module = getEnabledModule();
        if (module == null || module.mc.player == null || module.mc.player.getEntityId() != entityId) return null;
        return PendingResponse.entity(entityId, module.horizontalResponse(), module.verticalResponse());
    }

    /** Snapshot a vanilla explosion impulse so only that impulse is reduced. */
    public static PendingResponse captureExplosion(float motionX, float motionY, float motionZ) {
        Velocity module = getEnabledModule();
        if (module == null || module.mc.player == null || module.explosions == 0) return null;
        return PendingResponse.explosion(module.horizontalResponse(), module.verticalResponse(), motionX, motionY, motionZ);
    }

    /** Called on the client thread after vanilla has applied SPacketEntityVelocity. */
    public static void apply(PendingResponse response) {
        if (response == null) return;
        net.minecraft.client.entity.EntityPlayerSP player = net.minecraft.client.Minecraft.getMinecraft().player;
        if (player == null) return;
        if (response.explosion) {
            // SPacketExplosion adds its impulse to existing movement. Remove only
            // the configured portion of that delta instead of scaling sprinting
            // or any other motion the player already had.
            player.motionX -= response.motionX * response.horizontalReduction();
            player.motionZ -= response.motionZ * response.horizontalReduction();
            player.motionY -= response.motionY * response.verticalReduction();
            return;
        }
        if (player.getEntityId() != response.entityId) return;
        player.motionX *= response.horizontalResponse();
        player.motionZ *= response.horizontalResponse();
        player.motionY *= response.verticalResponse();
    }

    private static Velocity getEnabledModule() {
        dev.goldforge.module.Module module = dev.goldforge.GoldForge.MODULES.get("Velocity");
        return module instanceof Velocity && module.enabled && !module.isSuspended() ? (Velocity) module : null;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static int ncpReduction(int value) { return Math.min(40, clamp(value)); }
    private double horizontalResponse() { return 1.0D - (mode == 0 ? ncpReduction(horizontalPercent) : clamp(horizontalPercent)) / 100.0D; }
    private double verticalResponse() { return 1.0D - (mode == 0 ? 0 : clamp(verticalPercent)) / 100.0D; }

    public static final class PendingResponse {
        private final int entityId;
        private final boolean explosion;
        private final double horizontalResponse, verticalResponse;
        private final float motionX, motionY, motionZ;
        private PendingResponse(int entityId, boolean explosion, double horizontalResponse, double verticalResponse,
                                float motionX, float motionY, float motionZ) {
            this.entityId = entityId;
            this.explosion = explosion;
            this.horizontalResponse = horizontalResponse;
            this.verticalResponse = verticalResponse;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
        }
        private static PendingResponse entity(int entityId, double horizontalResponse, double verticalResponse) {
            return new PendingResponse(entityId, false, horizontalResponse, verticalResponse, 0F, 0F, 0F);
        }
        private static PendingResponse explosion(double horizontalResponse, double verticalResponse,
                                                 float motionX, float motionY, float motionZ) {
            return new PendingResponse(-1, true, horizontalResponse, verticalResponse, motionX, motionY, motionZ);
        }
        private double horizontalResponse() { return horizontalResponse; }
        private double verticalResponse() { return verticalResponse; }
        private double horizontalReduction() { return 1.0D - horizontalResponse; }
        private double verticalReduction() { return 1.0D - verticalResponse; }
    }
}
