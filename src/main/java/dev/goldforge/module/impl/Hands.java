package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumHand;
import net.minecraftforge.client.event.RenderSpecificHandEvent;

/** Independent first-person mainhand and offhand position, rotation and scale. */
public final class Hands extends Module {
    public int mainX, mainY, mainZ, mainRotX, mainRotY, mainRotZ, mainScale = 100;
    public int offX, offY, offZ, offRotX, offRotY, offRotZ, offScale = 100;
    private boolean drawing;

    public Hands() {
        super("Hands", "Adjusts each first-person hand's position, rotation and size.", Category.RENDER, 0);
    }

    public void openEditor() {
        if (mc.currentScreen != null) mc.displayGuiScreen(new dev.goldforge.gui.HandsEditor(mc.currentScreen, this));
    }

    public int get(int hand, int axis) {
        if (hand == 0) {
            switch (axis) {
                case 0: return mainX; case 1: return mainY; case 2: return mainZ;
                case 3: return mainRotX; case 4: return mainRotY; case 5: return mainRotZ;
                default: return mainScale;
            }
        }
        switch (axis) {
            case 0: return offX; case 1: return offY; case 2: return offZ;
            case 3: return offRotX; case 4: return offRotY; case 5: return offRotZ;
            default: return offScale;
        }
    }

    public void set(int hand, int axis, int value) {
        int v = Math.max(axis < 3 ? -200 : axis < 6 ? -180 : 25,
                Math.min(axis < 3 ? 200 : axis < 6 ? 180 : 250, value));
        if (hand == 0) {
            switch (axis) {
                case 0: mainX = v; break; case 1: mainY = v; break; case 2: mainZ = v; break;
                case 3: mainRotX = v; break; case 4: mainRotY = v; break; case 5: mainRotZ = v; break;
                default: mainScale = v;
            }
        } else {
            switch (axis) {
                case 0: offX = v; break; case 1: offY = v; break; case 2: offZ = v; break;
                case 3: offRotX = v; break; case 4: offRotY = v; break; case 5: offRotZ = v; break;
                default: offScale = v;
            }
        }
    }

    public void reset(int hand) {
        for (int axis = 0; axis < 6; ++axis) set(hand, axis, 0);
        set(hand, 6, 100);
    }

    /** Used by both normal rendering and ShaderFX's silhouette capture. */
    public void renderTransformed(RenderSpecificHandEvent event) {
        if (drawing) return;
        int hand = event.getHand() == EnumHand.MAIN_HAND ? 0 : 1;
        drawing = true;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(get(hand, 0) / 100F, get(hand, 1) / 100F, get(hand, 2) / 100F);
            GlStateManager.rotate(get(hand, 3), 1F, 0F, 0F);
            GlStateManager.rotate(get(hand, 4), 0F, 1F, 0F);
            GlStateManager.rotate(get(hand, 5), 0F, 0F, 1F);
            float scale = get(hand, 6) / 100F;
            GlStateManager.scale(scale, scale, scale);
            mc.entityRenderer.itemRenderer.renderItemInFirstPerson(mc.player,
                    event.getPartialTicks(), event.getInterpolatedPitch(), event.getHand(),
                    event.getSwingProgress(), event.getItemStack(), event.getEquipProgress());
        } finally {
            GlStateManager.popMatrix();
            drawing = false;
        }
    }

    public void onSpecificHand(RenderSpecificHandEvent event) {
        if (drawing || event.isCanceled() || mc.player == null || mc.world == null
                || mc.gameSettings.thirdPersonView != 0 || mc.player.isSpectator()) return;
        renderTransformed(event);
        event.setCanceled(true);
    }
}
