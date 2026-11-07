package dev.goldforge.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/** Small red core grows to a full green cube using the controller's real break progress. */
final class MiningVisuals {
    private static final Field DAMAGE = field("curBlockDamageMP", "field_78770_f");
    private static final Field CURRENT = field("currentBlock", "field_178895_c");

    private MiningVisuals() {}

    static float progress(Minecraft mc, BlockPos pos) {
        if (mc.playerController == null || DAMAGE == null || CURRENT == null) return 0F;
        try {
            if (!pos.equals(CURRENT.get(mc.playerController))) return 0F;
            return Math.max(0F, Math.min(1F, DAMAGE.getFloat(mc.playerController)));
        } catch (IllegalAccessException ignored) { return 0F; }
    }

    static void draw(Minecraft mc, BlockPos pos, float progress, long startedAt, boolean throughWalls) {
        float p = Math.max(0F, Math.min(1F, progress));
        float eased = p * p * (3F - 2F * p);
        double size = .18D + .78D * eased;
        double inset = (1D - size) * .5D;
        float fade = Math.min(1F, (System.currentTimeMillis() - startedAt) / 180F);
        float red = 1F - eased, green = eased;
        double x = mc.getRenderManager().viewerPosX;
        double y = mc.getRenderManager().viewerPosY;
        double z = mc.getRenderManager().viewerPosZ;
        AxisAlignedBB box = new AxisAlignedBB(pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1D - inset, pos.getY() + 1D - inset, pos.getZ() + 1D - inset).offset(-x, -y, -z);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            if (throughWalls) GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            RenderGlobal.renderFilledBox(box, red, green, .05F, .22F * fade);
            RenderGlobal.drawSelectionBoundingBox(box, red, green, .05F, .95F * fade);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.popMatrix();
        }
    }

    private static Field field(String name, String srg) {
        try { return ReflectionHelper.findField(PlayerControllerMP.class, name, srg); }
        catch (Exception ignored) { return null; }
    }
}
