package dev.goldforge.gui;

import cc.polyfrost.oneconfig.libs.universal.UKeyboard;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.gui.OneUIScreen;
import dev.goldforge.ConfigManager;
import dev.goldforge.module.impl.Hands;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

/** Visual two-hand editor with move, rotate and resize controls. */
public final class HandsEditor extends OneUIScreen {
    private static final int BG = 0xF00B0D11;
    private static final int PANEL = 0xEF15191F;
    private static final int CARD = 0xFF20262E;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int MUTED = 0xFF9DA6B2;
    private static final int YELLOW = 0xFFFFCD25;
    private static final int RED = 0xFFFF6572;
    private static final int GREEN = 0xFF52D39C;
    private static final int BLUE = 0xFF65A9FF;
    private final GuiScreen parent;
    private final Hands hands;
    private int hand;
    private int mode;
    private int dragging = -1;
    private int gizmoAxis = -1;
    private float gizmoStartX, gizmoStartY;
    private int gizmoStartValue;

    public HandsEditor(GuiScreen parent, Hands hands) {
        super(false);
        this.parent = parent;
        this.hands = hands;
    }

    @Override public void initScreen(int screenWidth, int screenHeight) {}
    @Override public boolean hasBackgroundBlur() { return true; }

    private Layout layout() {
        float scale = Math.max(.01F, Math.min(mc.displayWidth / 900F, mc.displayHeight / 570F));
        float screenW = mc.displayWidth / scale;
        float screenH = mc.displayHeight / scale;
        float w = Math.min(840F, screenW - 28F);
        float h = Math.min(490F, screenH - 28F);
        return new Layout(scale, screenW, screenH, w, h);
    }

    private static final class Layout {
        final float scale, screenW, screenH, x, y, w, h;
        final float previewX, previewY, previewW, previewH;

        Layout(float scale, float screenW, float screenH, float w, float h) {
            this.scale = scale;
            this.screenW = screenW;
            this.screenH = screenH;
            this.w = w;
            this.h = h;
            x = (screenW - w) * .5F;
            y = (screenH - h) * .5F;
            previewX = x + 24F;
            previewY = y + 124F;
            previewW = Math.max(220F, w * .48F - 30F);
            previewH = h - 165F;
        }
    }

    @Override public void draw(long vg, float partialTicks, InputHandler input) {
        NanoVGHelper n = NanoVGHelper.INSTANCE;
        Layout layout = layout();
        input.scale(layout.scale, layout.scale);
        n.scale(vg, layout.scale, layout.scale);
        float w = layout.w, h = layout.h, x = layout.x, y = layout.y;
        n.drawRect(vg, 0, 0, layout.screenW, layout.screenH, 0x7704070B);
        n.drawDropShadow(vg, x, y, w, h, 22F, 0F, 8F);
        n.drawRoundedRect(vg, x, y, w, h, BG, 18F);
        n.drawHollowRoundRect(vg, x, y, w, h, 0xFF4F4316, 18F, 1F);
        label(vg, "HANDS", x + 24F, y + 18F, 22F, TEXT);
        label(vg, "Tune the first-person view. Changes preview live in game.", x + 24F, y + 47F, 11F, MUTED);
        button(vg, input, x + w - 155F, y + 20F, 65F, 30F, hands.enabled ? "ON" : "OFF", hands.enabled);
        if (input.isAreaClicked(x + w - 155F, y + 20F, 65F, 30F)) hands.toggle();
        button(vg, input, x + w - 82F, y + 20F, 55F, 30F, "DONE", false);
        if (input.isAreaClicked(x + w - 82F, y + 20F, 55F, 30F)) close();

        float tabY = y + 76F;
        button(vg, input, x + 24F, tabY, 120F, 32F, "MAINHAND", hand == 0);
        button(vg, input, x + 152F, tabY, 120F, 32F, "OFFHAND", hand == 1);
        if (input.isAreaClicked(x + 24F, tabY, 120F, 32F)) { hand = 0; dragging = gizmoAxis = -1; }
        if (input.isAreaClicked(x + 152F, tabY, 120F, 32F)) { hand = 1; dragging = gizmoAxis = -1; }

        float previewX = layout.previewX, previewY = layout.previewY;
        float previewW = layout.previewW, previewH = layout.previewH;
        n.drawRoundedRect(vg, previewX, previewY, previewW, previewH, PANEL, 14F);
        n.drawHollowRoundRect(vg, previewX, previewY, previewW, previewH, 0xFF2B323A, 14F, 1F);
        label(vg, hand == 0 ? "LIVE VIEW · MAINHAND" : "LIVE VIEW · OFFHAND", previewX + 16F, previewY + 14F, 11F, MUTED);
        drawGizmo(vg, previewX, previewY, previewW, previewH);
        handleGizmo(input, previewX, previewY, previewW, previewH);

        float controlsX = previewX + previewW + 18F;
        float controlsW = x + w - 24F - controlsX;
        n.drawRoundedRect(vg, controlsX, previewY, controlsW, previewH, PANEL, 14F);
        n.drawHollowRoundRect(vg, controlsX, previewY, controlsW, previewH, 0xFF2B323A, 14F, 1F);
        float modeY = previewY + 14F, gap = 6F, modeW = (controlsW - 32F - gap * 2F) / 3F;
        String[] modes = {"MOVE", "ROTATE", "SIZE"};
        for (int i = 0; i < 3; ++i) {
            float bx = controlsX + 16F + i * (modeW + gap);
            button(vg, input, bx, modeY, modeW, 30F, modes[i], mode == i);
            if (input.isAreaClicked(bx, modeY, modeW, 30F)) { mode = i; dragging = gizmoAxis = -1; }
        }

        int start = mode == 0 ? 0 : mode == 1 ? 3 : 6;
        int count = mode == 2 ? 1 : 3;
        String[] axes = mode == 0 ? new String[]{"X · LEFT / RIGHT", "Y · UP / DOWN", "Z · NEAR / FAR"}
                : mode == 1 ? new String[]{"X · PITCH", "Y · YAW", "Z · ROLL"}
                : new String[]{"UNIFORM SCALE"};
        int[] colors = {RED, GREEN, BLUE};
        float rowStart = previewY + 60F, rowStep = Math.min(78F, (previewH - 112F) / Math.max(1, count));
        for (int i = 0; i < count; ++i) {
            int axis = start + i;
            float sy = rowStart + rowStep * i;
            drawSlider(vg, input, controlsX + 18F, sy, controlsW - 36F, axis, axes[i], colors[i]);
        }
        float resetY = previewY + previewH - 46F;
        button(vg, input, controlsX + 18F, resetY, controlsW - 36F, 30F, "RESET THIS HAND", false);
        if (input.isAreaClicked(controlsX + 18F, resetY, controlsW - 36F, 30F)) {
            hands.reset(hand);
            ConfigManager.save();
        }
        label(vg, "Drag a colored handle or slider. ESC returns to modules.", x + 24F, y + h - 29F, 11F, MUTED);
    }

    private void drawGizmo(long vg, float x, float y, float w, float h) {
        NanoVGHelper n = NanoVGHelper.INSTANCE;
        float cx = x + w * .5F + hands.get(hand, 0) * .19F;
        float cy = y + h * .52F - hands.get(hand, 1) * .19F;
        float size = 58F * hands.get(hand, 6) / 100F;
        if (mode == 0) {
            n.drawRoundedRect(vg, cx - 92F, cy - 1F, 184F, 2F, RED, 1F);
            n.drawRoundedRect(vg, cx - 1F, cy - 88F, 2F, 176F, GREEN, 1F);
            n.drawCircle(vg, cx + 92F, cy, 5F, RED);
            n.drawCircle(vg, cx, cy - 88F, 5F, GREEN);
            n.drawCircle(vg, cx, cy, 7F, BLUE);
        } else if (mode == 1) {
            n.drawCircle(vg, cx, cy, 88F, BLUE);
            n.drawCircle(vg, cx, cy, 85F, PANEL);
            n.drawCircle(vg, cx, cy, 65F, GREEN);
            n.drawCircle(vg, cx, cy, 62F, PANEL);
            n.drawRoundedRect(vg, cx - 1F, cy - 88F, 2F, 176F, RED, 1F);
            n.drawCircle(vg, cx, cy - 88F, 6F, RED);
            n.drawCircle(vg, cx + 65F, cy, 6F, GREEN);
            n.drawCircle(vg, cx + 88F, cy, 6F, BLUE);
        } else {
            float edge = size + 18F;
            n.drawHollowRoundRect(vg, cx - edge * .5F, cy - edge * .5F,
                    edge, edge, BLUE, 4F, 2F);
            n.drawCircle(vg, cx - edge * .5F, cy - edge * .5F, 5F, GREEN);
            n.drawCircle(vg, cx + edge * .5F, cy + edge * .5F, 5F, RED);
        }
        label(vg, mode == 0 ? "POSITION" : mode == 1 ? "ROTATION" : "SCALE",
                x + 17F, y + h - 28F, 11F, YELLOW);
    }

    /** Runs after NanoVG's frame so Minecraft's 3D renderer cannot corrupt its GL state. */
    public void renderLiveHands(float partialTicks) {
        if (mc.player == null || mc.world == null) return;
        Layout layout = layout();
        float scale = layout.scale;
        int px = Math.round((layout.previewX + 2F) * scale);
        int py = mc.displayHeight - Math.round((layout.previewY + layout.previewH - 2F) * scale);
        int pw = Math.round((layout.previewW - 4F) * scale);
        int ph = Math.round((layout.previewH - 64F) * scale);
        if (pw <= 0 || ph <= 0) return;

        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(px, py + Math.round(32F * scale), pw, ph);
            GL11.glViewport(px, py, pw, Math.round(layout.previewH * scale));
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            Project.gluPerspective(70F, pw / (float) Math.round(layout.previewH * scale), 0.05F, 100F);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.color(1F, 1F, 1F, 1F);
            RenderHelper.enableStandardItemLighting();
            hands.renderPreview(partialTicks);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(matrixMode);
            GL11.glPopAttrib();
        }
    }

    private void handleGizmo(InputHandler input, float x, float y, float w, float h) {
        float cx = x + w * .5F + hands.get(hand, 0) * .19F;
        float cy = y + h * .52F - hands.get(hand, 1) * .19F;
        float size = 58F * hands.get(hand, 6) / 100F;
        int axis = -1;
        if (mode == 0) {
            if (input.isAreaClicked(cx + 81F, cy - 12F, 24F, 24F)) axis = 0;
            else if (input.isAreaClicked(cx - 12F, cy - 100F, 24F, 24F)) axis = 1;
            else if (input.isAreaClicked(cx - 12F, cy - 12F, 24F, 24F)) axis = 2;
        } else if (mode == 1) {
            if (input.isAreaClicked(cx - 12F, cy - 100F, 24F, 24F)) axis = 3;
            else if (input.isAreaClicked(cx + 53F, cy - 12F, 24F, 24F)) axis = 4;
            else if (input.isAreaClicked(cx + 76F, cy - 12F, 24F, 24F)) axis = 5;
        } else {
            float edge = size + 18F;
            if (input.isAreaClicked(cx + edge * .5F - 13F, cy + edge * .5F - 13F, 26F, 26F)) axis = 6;
        }
        if (axis >= 0) {
            gizmoAxis = axis;
            gizmoStartX = input.mouseX();
            gizmoStartY = input.mouseY();
            gizmoStartValue = hands.get(hand, axis);
        }
        if (gizmoAxis < 0) return;
        if (!Mouse.isButtonDown(0)) {
            gizmoAxis = -1;
            ConfigManager.save();
            return;
        }
        float dx = input.mouseX() - gizmoStartX;
        float dy = input.mouseY() - gizmoStartY;
        int delta = gizmoAxis == 0 ? Math.round(dx / .19F)
                : gizmoAxis == 1 ? -Math.round(dy / .19F)
                : gizmoAxis == 2 ? Math.round((dx - dy) / .26F)
                : gizmoAxis == 6 ? Math.round(dx - dy)
                : Math.round(dx * 1.5F);
        hands.set(hand, gizmoAxis, gizmoStartValue + delta);
    }

    private void drawSlider(long vg, InputHandler input, float x, float y, float w,
            int axis, String name, int color) {
        NanoVGHelper n = NanoVGHelper.INSTANCE;
        int min = axis < 3 ? -200 : axis < 6 ? -180 : 25;
        int max = axis < 3 ? 200 : axis < 6 ? 180 : 250;
        int value = hands.get(hand, axis);
        label(vg, name, x, y, 11F, MUTED);
        String formatted = axis < 3 ? String.format(java.util.Locale.ROOT, "%+.2f", value / 100F)
                : axis < 6 ? value + "°" : value + "%";
        right(vg, formatted, x + w, y, 11F, TEXT);
        float barX = x + 25F, barY = y + 27F, barW = w - 50F;
        n.drawRoundedRect(vg, barX, barY, barW, 5F, CARD, 3F);
        float progress = (value - min) / (float) (max - min);
        n.drawRoundedRect(vg, barX, barY, Math.max(2F, barW * progress), 5F, color, 3F);
        n.drawCircle(vg, barX + barW * progress, barY + 2.5F, 7F, color);
        label(vg, "−", x + 2F, y + 21F, 17F, TEXT);
        label(vg, "+", x + w - 13F, y + 21F, 17F, TEXT);
        if (input.isAreaClicked(x, barY - 12F, 20F, 26F)) { hands.set(hand, axis, value - (axis < 3 ? 5 : axis < 6 ? 5 : 5)); ConfigManager.save(); }
        if (input.isAreaClicked(x + w - 20F, barY - 12F, 20F, 26F)) { hands.set(hand, axis, value + 5); ConfigManager.save(); }
        if (input.isAreaClicked(barX, barY - 11F, barW, 26F)) dragging = axis;
        if (dragging == axis) {
            if (Mouse.isButtonDown(0)) {
                float t = Math.max(0F, Math.min(1F, (input.mouseX() - barX) / barW));
                hands.set(hand, axis, Math.round(min + t * (max - min)));
            } else {
                dragging = -1;
                ConfigManager.save();
            }
        }
    }

    private void button(long vg, InputHandler input, float x, float y, float w, float h,
            String text, boolean active) {
        NanoVGHelper n = NanoVGHelper.INSTANCE;
        int color = active ? 0xFF4B3A0B : input.isAreaHovered(x, y, w, h) ? CARD : 0xFF1A2026;
        n.drawRoundedRect(vg, x, y, w, h, color, 9F);
        float tw = n.getTextWidth(vg, text, 11F, Fonts.REGULAR);
        label(vg, text, x + (w - tw) * .5F, y + (h - 11F) * .5F, 11F, active ? YELLOW : TEXT);
    }

    private void label(long vg, String text, float x, float y, float size, int color) {
        NanoVGHelper.INSTANCE.drawText(vg, text, x, y + size * .5F, color, size, Fonts.REGULAR);
    }
    private void right(long vg, String text, float x, float y, float size, int color) {
        label(vg, text, x - NanoVGHelper.INSTANCE.getTextWidth(vg, text, size, Fonts.REGULAR), y, size, color);
    }
    private void close() { ConfigManager.save(); mc.displayGuiScreen(parent); }
    @Override public void onKeyPressed(int keyCode, char typedChar, UKeyboard.Modifiers modifiers) {
        if (keyCode == Keyboard.KEY_ESCAPE) close();
    }
    @Override public void onScreenClose() { ConfigManager.save(); }
}
