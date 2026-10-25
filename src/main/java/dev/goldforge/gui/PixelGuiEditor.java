package dev.goldforge.gui;

import dev.goldforge.ConfigManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/** In-game editor for the pixel category windows. */
public final class PixelGuiEditor extends GuiScreen {
    private static final String[] THEMES = {"GOLD", "RED", "MINT", "BLUE"};
    private static final int[] ACCENTS = {0xFFF4CE38, 0xFFE35D65, 0xFF6DE0BB, 0xFF75BBF7};
    private final GuiScreen parent;
    private boolean draggingTransparency;

    public PixelGuiEditor(GuiScreen parent) { this.parent = parent; }

    private int x() { return (width - 260) / 2; }
    private int y() { return (height - 180) / 2; }
    private void label(String value, int x, int y, int color) {
        fontRenderer.drawString(value, x, y, color);
    }
    private void box(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + h, 0xFF494949);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }
    @Override public void drawScreen(int mx, int my, float partialTicks) {
        if (parent != null) parent.drawScreen(mx, my, partialTicks);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        int x = x(), y = y();
        box(x, y, 260, 180, 0xF0151515);
        Gui.drawRect(x + 1, y + 1, x + 259, y + 23, ACCENTS[GuiPreferences.theme]);
        label("GUI EDITOR", x + 9, y + 8, 0xFF101010);
        label("X", x + 244, y + 8, 0xFF101010);
        label("WINDOW THEME", x + 10, y + 35, 0xFFE7E7DF);
        for (int i = 0; i < THEMES.length; i++) {
            int bx = x + 10 + i * 61;
            box(bx, y + 49, 56, 23, ACCENTS[i]);
            label(THEMES[i], bx + 6, y + 57, 0xFF111111);
            if (GuiPreferences.theme == i)
                Gui.drawRect(bx, y + 74, bx + 56, y + 76, ACCENTS[i]);
        }
        label("WINDOW TRANSPARENCY", x + 10, y + 87, 0xFFE7E7DF);
        box(x + 10, y + 102, 25, 22, 0xFF444444);
        label("-", x + 20, y + 109, 0xFFFFFFFF);
        box(x + 224, y + 102, 25, 22, 0xFF444444);
        label("+", x + 233, y + 109, 0xFFFFFFFF);
        Gui.drawRect(x + 42, y + 109, x + 217, y + 117, 0xFF343434);
        int fill = 175 * GuiPreferences.pixelTransparency / 100;
        Gui.drawRect(x + 42, y + 109, x + 42 + fill, y + 117, ACCENTS[GuiPreferences.theme]);
        label(GuiPreferences.pixelTransparency + "%",
                x + 113, y + 127, 0xFFE7E7DF);
        box(x + 10, y + 148, 239, 23, 0xFF808080);
        for (int px = x + 11; px < x + 248; px += 10)
            for (int py = y + 149; py < y + 170; py += 10)
                if (((px - x) / 10 + (py - y) / 10) % 2 == 0)
                    Gui.drawRect(px, py, Math.min(px + 10, x + 248), Math.min(py + 10, y + 170), 0xFFC0C0C0);
        int alpha = Math.round(255F * (100 - GuiPreferences.pixelTransparency) / 100F);
        Gui.drawRect(x + 11, y + 149, x + 248, y + 170, (alpha << 24) | 0x141515);
        label("PREVIEW  " + THEMES[GuiPreferences.theme], x + 17, y + 155,
                ACCENTS[GuiPreferences.theme]);
    }
    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        int x = x(), y = y();
        if (mx >= x + 238 && mx < x + 260 && my >= y && my < y + 23) {
            close(); return;
        }
        if (my >= y + 49 && my < y + 72) {
            for (int i = 0; i < THEMES.length; i++) {
                int bx = x + 10 + i * 61;
                if (mx >= bx && mx < bx + 56) {
                    GuiPreferences.theme = i; ConfigManager.save(); return;
                }
            }
        }
        if (my >= y + 102 && my < y + 124) {
            if (mx >= x + 10 && mx < x + 35)
                GuiPreferences.pixelTransparency = Math.max(0, GuiPreferences.pixelTransparency - 5);
            else if (mx >= x + 224 && mx < x + 249)
                GuiPreferences.pixelTransparency = Math.min(100, GuiPreferences.pixelTransparency + 5);
            else if (mx >= x + 42 && mx < x + 217) {
                draggingTransparency = true;
                setTransparency(mx);
            }
            ConfigManager.save();
        }
    }
    private void setTransparency(int mx) {
        GuiPreferences.pixelTransparency = Math.max(0, Math.min(100,
                Math.round((mx - x() - 42) * 100F / 175F)));
    }
    @Override protected void mouseClickMove(int mx, int my, int button, long time) {
        if (button == 0 && draggingTransparency) setTransparency(mx);
    }
    @Override protected void mouseReleased(int mx, int my, int button) {
        if (button == 0 && draggingTransparency) {
            draggingTransparency = false;
            ConfigManager.save();
        }
    }
    private void close() { ConfigManager.save(); mc.displayGuiScreen(parent); }
    @Override protected void keyTyped(char ch, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { close(); return; }
        super.keyTyped(ch, key);
    }
    @Override public void onGuiClosed() { ConfigManager.save(); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void drawDefaultBackground() {}
}

