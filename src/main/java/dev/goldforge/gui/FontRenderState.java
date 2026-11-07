package dev.goldforge.gui;

import org.lwjgl.opengl.GL11;

/** Raw, scoped state avoids stale GlStateManager flags left by legacy GUI primitives. */
final class FontRenderState {
    private FontRenderState() {}
    static void begin() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
    }
    static void end() { GL11.glPopAttrib(); }
}
