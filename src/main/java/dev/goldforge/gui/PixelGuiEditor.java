package dev.goldforge.gui;

import dev.goldforge.ConfigManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** In-game editor for the pixel category windows. */
public final class PixelGuiEditor extends GuiScreen {
    private static final String[] THEMES = {"GOLD", "RED", "MINT", "BLUE"};
    private static final int[] ACCENTS = {0xFFF4CE38, 0xFFE35D65, 0xFF6DE0BB, 0xFF75BBF7};
    private final GuiScreen parent;

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
        Gui.drawRect(0, 0, width, height, 0x89000000);
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
        label("WINDOW OPACITY", x + 10, y + 87, 0xFFE7E7DF);
        box(x + 10, y + 102, 25, 22, 0xFF444444);
        label("-", x + 20, y + 109, 0xFFFFFFFF);
        box(x + 224, y + 102, 25, 22, 0xFF444444);
        label("+", x + 233, y + 109, 0xFFFFFFFF);
        Gui.drawRect(x + 42, y + 109, x + 217, y + 117, 0xFF343434);
        int fill = 175 * GuiPreferences.pixelOpacity / 255;
        Gui.drawRect(x + 42, y + 109, x + 42 + fill, y + 117, ACCENTS[GuiPreferences.theme]);
        label(Integer.toString(Math.round(GuiPreferences.pixelOpacity * 100F / 255F)) + "%",
                x + 113, y + 127, 0xFFE7E7DF);
        int preview = (GuiPreferences.pixelOpacity << 24) | 0x141515;
        box(x + 10, y + 148, 239, 23, preview);
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
                GuiPreferences.pixelOpacity = Math.max(32, GuiPreferences.pixelOpacity - 16);
            else if (mx >= x + 224 && mx < x + 249)
                GuiPreferences.pixelOpacity = Math.min(255, GuiPreferences.pixelOpacity + 16);
            else if (mx >= x + 42 && mx < x + 217)
                GuiPreferences.pixelOpacity = Math.max(32, Math.min(255,
                        (mx - x - 42) * 255 / 175));
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

