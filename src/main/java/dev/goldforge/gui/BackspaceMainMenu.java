package dev.goldforge.gui;

import dev.goldforge.GoldForge;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.io.IOException;

/** Animated Backspace title screen; vanilla screens remain responsible for world and network flows. */
public final class BackspaceMainMenu extends GuiScreen {
    private static final int WHITE = 0xFFF5F4EF;
    private static final int MUTED = 0xFF8E929D;
    private static final int GOLD = 0xFFFFD21F;
    private static final int DARK = 0xFF050608;
    private static final int PLAY = 0, SETTINGS = 1, MODULES = 2, QUIT = 3;
    private static final ResourceLocation PLAY_ICON = new ResourceLocation("goldforge", "textures/gui/menu/play.png");
    private static final ResourceLocation SETTINGS_ICON = new ResourceLocation("goldforge", "textures/gui/menu/settings.png");
    private static final ResourceLocation MODULES_ICON = new ResourceLocation("goldforge", "textures/gui/menu/modules.png");
    private static final ResourceLocation BRAND_MARK = new ResourceLocation("goldforge", "textures/gui/backspace_mark.png");
    private static final ResourceLocation[] MENU_ICONS = {PLAY_ICON, SETTINGS_ICON, MODULES_ICON, null};
    private static final int[] ICON_WIDTHS = {95, 100, 91, 0};
    private static final int[] ICON_HEIGHTS = {94, 110, 88, 0};
    private UiFont uiFont;
    private long openedAt;
    private long lastFrameAt;
    private float menuFade;
    private float[] hover = new float[4];
    private int selected = -1;
    private boolean playExpanded;
    private float submenuFade;
    private float playHoverSeconds;
    private float quitFade;
    private boolean keyboardFocus;
    private int lastMouseX = Integer.MIN_VALUE, lastMouseY = Integer.MIN_VALUE;

    @Override public void initGui() {
        openedAt = System.currentTimeMillis();
        lastFrameAt = openedAt;
        menuFade = 0F;
        submenuFade = 0F;
        playHoverSeconds = 0F;
        try { uiFont = new UiFont(null, 16F); }
        catch (Exception ignored) { uiFont = null; }
    }

    @Override public void onGuiClosed() {
        if (uiFont != null) { uiFont.dispose(); uiFont = null; }
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float dt = MathHelper.clamp((now - lastFrameAt) / 1000F, 0F, 0.05F);
        lastFrameAt = now;
        if (mouseX != lastMouseX || mouseY != lastMouseY) keyboardFocus = false;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        float elapsed = (now - openedAt) / 1000F;
        menuFade = approach(menuFade, 1F, dt, 4.6F);
        quitFade = approach(quitFade, mc.isSingleplayer() || mc.world == null ? 1F : 0.35F, dt, 5F);

        drawRect(0, 0, width, height, DARK);
        MenuBackdropShader.draw(0, 0, width, height, elapsed);
        beginUiLayer();

        int left = leftMargin();
        float titleY = brandY() + (1F - menuFade) * 9F;
        float wordmarkWidth = uiFont != null
                ? uiFont.widthPrecise("backspace") * 2.02F
                : fontRenderer.getStringWidth("backspace") * 1.818F;
        drawBrandEmblem(left, titleY, wordmarkWidth, menuFade);
        GlStateManager.pushMatrix();
        GlStateManager.translate(left, titleY, 0F);
        GlStateManager.scale(2.02F, 2.02F, 1F);
        int titleWhite = fadeAlpha(WHITE, menuFade);
        int titleGold = fadeAlpha(GOLD, menuFade);
        if (uiFont != null) {
            uiFont.draw("back", 0F, 0F, titleWhite);
            uiFont.draw("space", uiFont.widthPrecise("back"), 0F, titleGold);
        } else {
            GlStateManager.scale(0.9F, 0.9F, 1F);
            fontRenderer.drawString("back", 0, 0, titleWhite, false);
            fontRenderer.drawString("space", fontRenderer.getStringWidth("back"), 0, titleGold, false);
        }
        GlStateManager.popMatrix();
        if (height >= 300) {
            label(GoldForge.VERSION.toUpperCase(java.util.Locale.ROOT).replace('-', ' '),
                    left + 2, (int)(titleY + 34F), fadeAlpha(MUTED, menuFade), 9F);
            label("A CLEANER WAY TO PLAY", left + 2, (int)(titleY + 54F), fadeAlpha(0xFF777A83, menuFade), 9F);
        }

        int rowY = menuRowY();
        int rowGap = menuRowGap();
        String[] names = {"Play", "Settings", "Modules", "Quit game"};
        int rowX = left - 12;
        int menuRight = left + 194;
        boolean overPlay = inside(mouseX, mouseY, rowX, rowY - 5, menuRight, rowY + 35);
        for (int i = 0; i < names.length; i++) {
            int y = rowY + i * rowGap;
            boolean over = inside(mouseX, mouseY, rowX, y - 5, menuRight, y + 35);
            if (over) selected = i;
            hover[i] = approach(hover[i], over || (keyboardFocus && selected == i) ? 1F : 0F, dt, 12F);
            float alpha = menuFade * (i == QUIT ? quitFade : 1F);
            int bgAlpha = Math.min(52, Math.round(hover[i] * 52F * alpha));
            if (bgAlpha > 0) drawRoundedRect(rowX, y - 5, menuRight, y + 35, 7F, (bgAlpha << 24) | 0x000A0A0D);
            int iconColor = fadeAlpha(blendColor(0xFFD5D7DB, GOLD, hover[i]), alpha);
            int iconSize = height < 320 ? 20 : 24;
            int labelSize = height < 320 ? 16 : 19;
            if (i < 3) drawMenuIcon(i, left + 4, y + (31 - iconSize) / 2, iconSize, iconColor);
            else drawQuitIcon(left + 15, y + 15, 13, iconColor);
            label(names[i], left + 39, y + (labelSize == 19 ? 4 : 7), fadeAlpha(blendColor(i == QUIT ? MUTED : WHITE, GOLD, hover[i]), alpha), labelSize);
            if (hover[i] > 0.01F) {
                drawRect(rowX, y - 5, rowX + 1, y + 35, (Math.round(210F * hover[i] * alpha) << 24) | 0x00FFD21F);
                drawRect(left + 39, y + 29, left + 39 + Math.round(20F * hover[i]), y + 30, (Math.round(170F * hover[i] * alpha) << 24) | 0x00FFD21F);
            }
        }

        if (overPlay) playHoverSeconds += dt; else playHoverSeconds = Math.max(0F, playHoverSeconds - dt * 1.8F);
        if (playHoverSeconds > 0.14F) playExpanded = true;
        else if (!inside(mouseX, mouseY, rowX, rowY - 14, left + 342, rowY + 66)
                && !(keyboardFocus && selected >= 4)) playExpanded = false;
        submenuFade = approach(submenuFade, playExpanded ? 1F : 0F, dt, 9F);
        if (submenuFade > 0.015F) drawPlaySubmenu(mouseX, mouseY, left, rowY, submenuFade, menuFade);

        label("1.12.2", left, height - 26, 0xFF6A6B73, 9F);
        String footer = "BACKSPACE  /  " + GoldForge.VERSION;
        fontRenderer.drawString(footer, width - fontRenderer.getStringWidth(footer) - 18, height - 25, 0xFF666872, false);
    }

    private void drawPlaySubmenu(int mouseX, int mouseY, int left, int y, float fade, float menuAlpha) {
        int spineX = left + 160;
        int branchX = spineX + 17;
        int singleY = y - 4;
        int multiY = y + 29;
        boolean overSingle = inside(mouseX, mouseY, branchX, singleY - 3, branchX + 132, singleY + 25);
        boolean overMulti = inside(mouseX, mouseY, branchX, multiY - 3, branchX + 132, multiY + 25);
        if (overSingle) selected = 4;
        if (overMulti) selected = 5;
        int lineAlpha = Math.min(230, Math.round(190F * fade * menuAlpha));
        int lineColor = (lineAlpha << 24) | 0x00FFD21F;
        int rootY = y + 16;
        drawRoundBranch(left + 101, rootY, spineX - 4, rootY, 2.6F, lineColor);
        drawRoundBranch(spineX - 4, rootY, branchX, singleY + 11, 2.6F, lineColor);
        drawRoundBranch(spineX - 4, rootY, branchX, multiY + 11, 2.6F, lineColor);
        drawCircle(spineX - 4, rootY, 4F, (lineAlpha << 24) | 0x00FFD21F);
        drawTreeEntry(branchX, singleY, "Singleplayer", overSingle, fade, menuAlpha);
        drawTreeEntry(branchX, multiY, "Multiplayer", overMulti, fade, menuAlpha);
    }

    private void drawTreeEntry(int x, int y, String text, boolean hovered, float fade, float menuAlpha) {
        drawRoundedRect(x - 6, y - 5, x + 142, y + 27, 12F,
                fadeAlpha(hovered ? 0xFF211B0D : 0x66101217, fade * menuAlpha));
        drawCircle(x + 7, y + 11, 3.4F, fadeAlpha(hovered ? GOLD : 0xFFAAA28A, fade * menuAlpha));
        label(text, x + 18, y + 2, fadeAlpha(hovered ? GOLD : WHITE, fade * menuAlpha), 14F);
    }

    private void drawMenuIcon(int index, int x, int y, int maxSize, int argb) {
        ResourceLocation texture = MENU_ICONS[index];
        if (texture == null) return;
        float scale = maxSize / (float)Math.max(ICON_WIDTHS[index], ICON_HEIGHTS[index]);
        int iconW = Math.max(1, Math.round(ICON_WIDTHS[index] * scale));
        int iconH = Math.max(1, Math.round(ICON_HEIGHTS[index] * scale));
        mc.getTextureManager().bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(((argb >>> 16) & 255) / 255F, ((argb >>> 8) & 255) / 255F,
                (argb & 255) / 255F, ((argb >>> 24) & 255) / 255F);
        // Render at the source dimensions then scale the model matrix. This avoids a 1.12 GUI
        // texture-coordinate edge case which sampled only a corner of non-256px icon textures.
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0F);
        GlStateManager.scale(iconW / (float) ICON_WIDTHS[index], iconH / (float) ICON_HEIGHTS[index], 1F);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0F, 0F, ICON_WIDTHS[index], ICON_HEIGHTS[index],
                ICON_WIDTHS[index], ICON_HEIGHTS[index]);
        GlStateManager.popMatrix();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.disableBlend();
    }

    /** The supplied transparent mark is deliberately tinted and composited beneath the type. */
    private void drawBrandEmblem(int left, float titleY, float wordmarkWidth, float fade) {
        int size = height < 320 ? 92 : 120;
        int x = Math.round(left + (wordmarkWidth - size) * 0.5F);
        int y = Math.round(titleY - (size - 34F) * 0.5F);
        mc.getTextureManager().bindTexture(BRAND_MARK);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        // Olive rather than bright yellow keeps this as a backdrop, while alpha provides the real fade.
        GlStateManager.color(0.63F, 0.49F, 0.05F, 0.19F * fade);
        // Match icon rendering: native texture coordinates plus a transform prevents partial texture samples.
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0F);
        GlStateManager.scale(size / 256F, size / 256F, 1F);
        drawTexturedModalRect(0, 0, 0, 0, 256, 256);
        GlStateManager.popMatrix();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.disableBlend();
    }

    /** A branch is built from overlapping discs, avoiding aliased one-pixel wire segments. */
    private static void drawRoundBranch(float startX, float startY, float endX, float endY, float radius, int color) {
        float bend = Math.max(9F, Math.abs(endX - startX) * 0.55F);
        for (int i = 0; i <= 26; i++) {
            float t = i / 26F, inv = 1F - t;
            float controlX = startX + bend;
            float controlY = startY + (endY - startY) * 0.50F;
            drawCircle(inv * inv * startX + 2F * inv * t * controlX + t * t * endX,
                    inv * inv * startY + 2F * inv * t * controlY + t * t * endY, radius, color);
        }
    }

    private static void drawCircle(float centerX, float centerY, float radius, int color) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GL11.glColor4ub((byte) (color >> 16), (byte) (color >> 8), (byte) color, (byte) (color >>> 24));
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(centerX, centerY);
            for (int i = 0; i <= 16; i++) {
                double angle = Math.PI * 2D * i / 16D;
                GL11.glVertex2f(centerX + (float) Math.cos(angle) * radius, centerY + (float) Math.sin(angle) * radius);
            }
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void drawRoundedRect(float left, float top, float right, float bottom, float radius, int color) {
        float r = Math.max(0F, Math.min(radius, Math.min((right - left) / 2F, (bottom - top) / 2F)));
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GL11.glColor4ub((byte) (color >> 16), (byte) (color >> 8), (byte) color, (byte) (color >>> 24));
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f((left + right) / 2F, (top + bottom) / 2F);
            for (int corner = 0; corner <= 4; corner++) {
                float cx = corner == 0 || corner == 3 || corner == 4 ? left + r : right - r;
                float cy = corner < 2 || corner == 4 ? top + r : bottom - r;
                float from = corner == 0 ? 180F : corner == 1 ? 270F : corner == 2 ? 0F : corner == 3 ? 90F : 180F;
                for (int step = 0; step <= 5; step++) {
                    double angle = Math.toRadians(from + step * 18F);
                    GL11.glVertex2f(cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r);
                }
            }
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void drawQuitIcon(int centerX, int centerY, int size, int color) {
        int thickness = Math.max(3, size / 5), half = Math.max(6, size / 2);
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0F);
        GlStateManager.rotate(45F, 0F, 0F, 1F);
        Gui.drawRect(-thickness / 2, -half, thickness / 2 + 1, half, color);
        Gui.drawRect(-half, -thickness / 2, half, thickness / 2 + 1, color);
        GlStateManager.popMatrix();
    }

    /** Restore every state the menu UI depends on after the isolated shader pass. */
    private static void beginUiLayer() {
        GL20.glUseProgram(0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) { super.mouseClicked(mouseX, mouseY, mouseButton); return; }
        keyboardFocus = false;
        int left = leftMargin();
        int rowX = left - 12;
        int rowY = menuRowY();
        int rowGap = menuRowGap();
        int childX = left + 177;
        if ((playExpanded || submenuFade > 0.25F) && inside(mouseX, mouseY, childX, rowY - 7, childX + 132, rowY + 21)) { openSingleplayer(); return; }
        if ((playExpanded || submenuFade > 0.25F) && inside(mouseX, mouseY, childX, rowY + 26, childX + 132, rowY + 54)) { openMultiplayer(); return; }
        if (inside(mouseX, mouseY, rowX, rowY - 5, left + 194, rowY + 35)) { playExpanded = !playExpanded; return; }
        if (inside(mouseX, mouseY, rowX, rowY + rowGap - 5, left + 194, rowY + rowGap + 35)) {
            mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)); return;
        }
        if (inside(mouseX, mouseY, rowX, rowY + rowGap * 2 - 5, left + 194, rowY + rowGap * 2 + 35)) {
            mc.displayGuiScreen(new PixelClickGui(this)); return;
        }
        if (inside(mouseX, mouseY, rowX, rowY + rowGap * 3 - 5, left + 194, rowY + rowGap * 3 + 35)) {
            if (mc.world == null) mc.shutdown(); else mc.displayGuiScreen(null);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void openSingleplayer() { mc.displayGuiScreen(new GuiWorldSelection(this)); }
    private void openMultiplayer() { mc.displayGuiScreen(new GuiMultiplayer(this)); }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (playExpanded) playExpanded = false;
            return;
        }
        if (keyCode == Keyboard.KEY_TAB || keyCode == Keyboard.KEY_DOWN) {
            keyboardFocus = true;
            selected = selected == 4 ? 5 : selected == 5 ? PLAY : (selected + 1) % 4;
            if (selected == PLAY || selected >= 4) playExpanded = true;
            else playExpanded = false;
            return;
        }
        if (keyCode == Keyboard.KEY_UP) {
            keyboardFocus = true;
            selected = selected == 5 ? 4 : selected == 4 ? PLAY : (selected + 3) % 4;
            if (selected == PLAY || selected >= 4) playExpanded = true;
            return;
        }
        if (keyCode == Keyboard.KEY_RIGHT && selected == PLAY) { keyboardFocus = true; playExpanded = true; selected = 4; return; }
        if (keyCode == Keyboard.KEY_RIGHT && selected == 4) { keyboardFocus = true; selected = 5; return; }
        if (keyCode == Keyboard.KEY_LEFT && selected >= 4) { keyboardFocus = true; selected = PLAY; return; }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || typedChar == ' ') {
            switch (selected) {
                case PLAY: if (playExpanded) openSingleplayer(); else playExpanded = true; break;
                case SETTINGS: mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)); break;
                case MODULES: mc.displayGuiScreen(new PixelClickGui(this)); break;
                case QUIT: if (mc.world == null) mc.shutdown(); else mc.displayGuiScreen(null); break;
                case 4: openSingleplayer(); break;
                case 5: openMultiplayer(); break;
                default: break;
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void label(String value, float x, float y, int color, float size) {
        if (uiFont != null) {
            GlStateManager.pushMatrix();
            float scale = Math.max(0.55F, size / 16F);
            GlStateManager.translate(x, y, 0F);
            GlStateManager.scale(scale, scale, 1F);
            uiFont.draw(value, 0F, 0F, color);
            GlStateManager.popMatrix();
        } else {
            GlStateManager.pushMatrix();
            float scale = Math.max(0.75F, size / 9F);
            GlStateManager.translate(x, y, 0F);
            GlStateManager.scale(scale, scale, 1F);
            fontRenderer.drawString(value, 0, 0, color, false);
            GlStateManager.popMatrix();
        }
    }

    private int menuRowGap() {
        if (height < 320) return 35;
        return Math.max(45, Math.min(56, Math.round(height * 0.095F)));
    }
    private int menuRowY() {
        int lowerBound = brandY() + (height < 360 ? 76 : 106);
        int bottomBound = height - menuRowGap() * 3 - (height < 320 ? 44 : 58);
        return Math.max(16, Math.min(Math.max(lowerBound, Math.round(height * 0.42F)), bottomBound));
    }
    private int leftMargin() { return Math.max(38, Math.min(154, Math.round(width * 0.085F))); }
    private int brandY() { return Math.max(46, Math.min(height - 210, Math.round(height * 0.155F))); }

    private static boolean inside(int mx, int my, int x, int y, int right, int bottom) { return mx >= x && mx <= right && my >= y && my <= bottom; }
    private static float approach(float value, float target, float dt, float speed) { return value + (target - value) * (1F - (float)Math.exp(-Math.max(0F, dt) * speed)); }
    private static int blendColor(int from, int to, float amount) {
        float f = MathHelper.clamp(amount, 0F, 1F);
        int r = Math.round(((from >> 16) & 255) * (1F - f) + ((to >> 16) & 255) * f);
        int g = Math.round(((from >> 8) & 255) * (1F - f) + ((to >> 8) & 255) * f);
        int b = Math.round((from & 255) * (1F - f) + (to & 255) * f);
        return (from & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
    private static int fadeAlpha(int color, float alpha) { return (Math.max(0, Math.min(255, Math.round(((color >>> 24) & 255) * alpha))) << 24) | (color & 0x00FFFFFF); }
}

