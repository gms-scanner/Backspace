package dev.goldforge.render;

import dev.goldforge.module.impl.Cape;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

/** Vanilla-style cloth motion with the bundled Backspace cape texture. */
public final class BackspaceCapeLayer implements LayerRenderer<AbstractClientPlayer> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("goldforge", "textures/entity/backspace_cape.png");
    private final RenderPlayer renderer;
    private final ModelBase capeBase;
    private final ModelRenderer cape;

    public BackspaceCapeLayer(RenderPlayer renderer) {
        this.renderer = renderer;
        this.capeBase = new CapeModelBase();
        this.cape = new ModelRenderer(capeBase, 0, 0);
        this.cape.addBox(-6.0F, 0.0F, -1.0F, 12, 16, 1);
    }

    @Override public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (!Cape.isEnabledFor(player) || player.isInvisible()) return;

        renderer.bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 0.125F);

        double dx = player.prevChasingPosX + (player.chasingPosX - player.prevChasingPosX) * partialTicks
                - (player.prevPosX + (player.posX - player.prevPosX) * partialTicks);
        double dy = player.prevChasingPosY + (player.chasingPosY - player.prevChasingPosY) * partialTicks
                - (player.prevPosY + (player.posY - player.prevPosY) * partialTicks);
        double dz = player.prevChasingPosZ + (player.chasingPosZ - player.prevChasingPosZ) * partialTicks
                - (player.prevPosZ + (player.posZ - player.prevPosZ) * partialTicks);
        float yaw = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
        double sinYaw = MathHelper.sin(yaw * 0.017453292F);
        double cosYaw = -MathHelper.cos(yaw * 0.017453292F);
        float forward = (float) dy * 10.0F;
        forward = MathHelper.clamp(forward, -6.0F, 32.0F);
        float side = (float) (dx * sinYaw + dz * cosYaw) * 100.0F;
        float sway = (float) (dx * cosYaw - dz * sinYaw) * 100.0F;
        if (side < 0.0F) side = 0.0F;
        float cameraYaw = player.prevCameraYaw + (player.cameraYaw - player.prevCameraYaw) * partialTicks;
        float walk = player.prevDistanceWalkedModified
                + (player.distanceWalkedModified - player.prevDistanceWalkedModified) * partialTicks;
        forward += MathHelper.sin(walk * 6.0F) * 32.0F * cameraYaw;
        if (player.isSneaking()) forward += 25.0F;

        GlStateManager.rotate(6.0F + side / 2.0F + forward, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(sway / 2.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(-sway / 2.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        cape.render(scale);
        GlStateManager.popMatrix();
    }

    @Override public boolean shouldCombineTextures() { return false; }

    private static final class CapeModelBase extends ModelBase {
        private CapeModelBase() { textureWidth = 64; textureHeight = 32; }
    }
}
