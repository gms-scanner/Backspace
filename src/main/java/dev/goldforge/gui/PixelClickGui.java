package dev.goldforge.gui;

import dev.goldforge.ConfigManager;
import dev.goldforge.GoldForge;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.module.ModuleSetting;
import dev.goldforge.module.impl.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;

/** Compact category windows with settings expanded directly below a module. */
public final class PixelClickGui extends GuiScreen {
    private static final int W = 136, GAP = 7, ROW = 17, TOP = 30, HEADER = 19, FOOT = 27;
    private final GuiScreen parent;
    private final EnumMap<Category, Integer> scroll = new EnumMap<Category, Integer>(Category.class);
    private int page, settingsScroll;
    private Module selected;
    private boolean binding;

    public PixelClickGui() { this(null); }
    public PixelClickGui(GuiScreen parent) { this.parent = parent; }

    private int columns() { return Math.max(1, (width - 16 + GAP) / (W + GAP)); }
    private int maxPage() { return Math.max(0, Category.values().length - columns()); }
    private int left(int column) { return 8 + column * (W + GAP); }
    private int bottom() { return height - FOOT; }
    private int accent() {
        switch (GuiPreferences.theme) {
            case 1: return 0xFFE35D65;
            case 2: return 0xFF6DE0BB;
            case 3: return 0xFF75BBF7;
            default: return 0xFFF4CE38;
        }
    }
    private int edge() {
        switch (GuiPreferences.theme) {
            case 1: return 0xFF60383B;
            case 2: return 0xFF31584F;
            case 3: return 0xFF354C62;
            default: return 0xFF544916;
        }
    }
    private int surface() { return (GuiPreferences.pixelOpacity << 24) | 0x141515; }
    private void box(int x, int y, int w, int h, int fill) {
        if (h <= 0) return;
        Gui.drawRect(x, y, x + w, y + h, edge());
        if (w > 2 && h > 2) Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }
    private void text(String value, int x, int y, int color) {
        fontRenderer.drawString(value, x, y, color);
    }
    private String fit(String value, int max) { return fontRenderer.trimStringToWidth(value, max); }
    private int offset(Category category) {
        Integer value = scroll.get(category);
        return value == null ? 0 : value;
    }
    private int settingsRows(int rowY) {
        if (selected == null) return 0;
        int available = bottom() - rowY - ROW;
        int fixed = 71 + (special() ? 17 : 0);
        return Math.max(0, Math.min(Math.min(5, selected.getSettings().size()), (available - fixed) / 18));
    }
    private int editorHeight(int rowY) {
        int available = bottom() - rowY - ROW;
        return Math.max(0, Math.min(available, 71 + settingsRows(rowY) * 18 + (special() ? 17 : 0)));
    }

    @Override public void drawScreen(int mx, int my, float partial) {
        Gui.drawRect(0, 0, width, height, 0x63000000);
        text("BACKSPACE", 8, 8, accent());
        text("<  CATEGORIES  >", Math.max(8, width - 100), 8, 0xFFE0E0D0);
        page = Math.min(page, maxPage());
        Category[] categories = Category.values();
        for (int col = 0; col < columns() && page + col < categories.length; col++)
            drawColumn(categories[page + col], left(col), mx, my);
        text("LMB TOGGLE  |  RMB SETTINGS  |  WHEEL SCROLL", 8, height - 13, 0xFFEAD993);
        box(width - 182, height - 19, 84, 16, accent());
        text("GUI EDITOR", width - 176, height - 15, 0xFF101010);
        box(width - 91, height - 19, 84, 16, accent());
        text("HUD EDITOR", width - 85, height - 15, 0xFF101010);
    }

    private void drawColumn(Category category, int x, int mx, int my) {
        List<Module> modules = GoldForge.MODULES.in(category);
        box(x, TOP, W, HEADER, accent());
        text(category.name(), x + 5, TOP + 5, 0xFF101010);
        text(Integer.toString(modules.size()), x + W - 18, TOP + 5, 0xFF101010);
        box(x, TOP + HEADER, W, bottom() - TOP - HEADER, surface());
        int start = Math.max(0, Math.min(offset(category), Math.max(0, modules.size() - 1)));
        scroll.put(category, start);
        int rowY = TOP + HEADER + 1;
        for (int index = start; index < modules.size() && rowY + ROW <= bottom(); index++) {
            Module module = modules.get(index);
            boolean hover = mx >= x && mx < x + W && my >= rowY && my < rowY + ROW;
            if (hover) Gui.drawRect(x + 1, rowY, x + W - 1, rowY + ROW, 0x806B591C);
            if (module.enabled) Gui.drawRect(x + 1, rowY, x + 3, rowY + ROW, accent());
            text(fit(module.name, W - 27), x + 6, rowY + 4, module.enabled ? accent() : 0xFFE5E5D6);
            circle(x + W - 12, rowY + 8, module.enabled);
            if (module == selected) {
                int h = editorHeight(rowY);
                if (h > 0) drawSettings(x, rowY + ROW, h);
                rowY += h;
            }
            rowY += ROW;
        }
        if (modules.size() > 0 && (start > 0 || rowY < TOP + HEADER + 1 + modules.size() * ROW))
            text((start + 1) + "-" + Math.min(modules.size(), start + Math.max(1, (bottom() - TOP - HEADER) / ROW))
                    + " / " + modules.size(), x + 4, bottom() + 3, accent());
    }

    private void circle(int cx, int cy, boolean enabled) {
        int[][] pixels = {{-2,-4},{-1,-4},{0,-4},{1,-4},{-3,-3},{2,-3},{-4,-2},{3,-2},
                {-4,-1},{3,-1},{-4,0},{3,0},{-3,1},{2,1},{-2,2},{-1,2},{0,2},{1,2}};
        for (int[] p : pixels) Gui.drawRect(cx + p[0], cy + p[1], cx + p[0] + 1, cy + p[1] + 1, accent());
        if (enabled) Gui.drawRect(cx - 2, cy - 2, cx + 2, cy + 1, accent());
    }

    private void drawSettings(int x, int y, int h) {
        box(x + 3, y, W - 6, h, surface());
        Gui.drawRect(x + 4, y + 1, x + 6, y + h - 1, accent());
        text(fit(selected.description, W - 16), x + 9, y + 4, 0xFFE4E4D8);
        int toggleY = y + 16;
        Gui.drawRect(x + 9, toggleY, x + W - 9, toggleY + 14,
                selected.enabled ? accent() : 0xFF494944);
        text(selected.enabled ? "ENABLED" : "DISABLED", x + 13, toggleY + 3,
                selected.enabled ? 0xFF101010 : 0xFFF1F1E4);
        List<ModuleSetting> options = selected.getSettings();
        int rows = settingsRows(y - ROW);
        settingsScroll = Math.max(0, Math.min(settingsScroll, Math.max(0, options.size() - rows)));
        for (int i = 0; i < rows; i++) {
            ModuleSetting option = options.get(settingsScroll + i);
            int sy = y + 34 + i * 18;
            Gui.drawRect(x + 9, sy, x + W - 9, sy + 17, 0xD02A2A28);
            text(fit(option.name, 64), x + 12, sy + 4, 0xFFE8E7D9);
            text("-", x + W - 57, sy + 4, accent());
            text(Integer.toString(option.get()), x + W - 43, sy + 4, accent());
            text("+", x + W - 18, sy + 4, accent());
        }
        int keyY = y + 34 + rows * 18;
        if (keyY + 16 <= y + h) {
            Gui.drawRect(x + 9, keyY, x + W - 9, keyY + 16, 0xD0423C23);
            text(binding ? "PRESS A KEY..." : "KEY: " + (selected.key == 0 ? "NONE" :
                    Keyboard.getKeyName(selected.key)), x + 12, keyY + 4, accent());
        }
        if (special() && keyY + 33 <= y + h)
            text("OPEN MODULE EDITOR", x + 9, keyY + 21, accent());
    }

    private boolean special() {
        return selected instanceof BlockESP || selected instanceof DIESP
                || selected instanceof AutoSign || selected instanceof Hands;
    }
    private void openEditor() {
        if (selected instanceof BlockESP) mc.displayGuiScreen(new BlockSelectGui(this, (BlockESP) selected));
        else if (selected instanceof DIESP) mc.displayGuiScreen(new ItemSelectGui(this, (DIESP) selected));
        else if (selected instanceof AutoSign) mc.displayGuiScreen(new SignTextGui(this, (AutoSign) selected));
        else if (selected instanceof Hands) ((Hands) selected).openEditor();
    }

    private boolean settingsClick(int x, int y, int h, int mx, int my) {
        if (mx < x + 3 || mx >= x + W - 3 || my < y || my >= y + h) return false;
        if (mx >= x + 9 && mx < x + W - 9 && my >= y + 16 && my < y + 30) {
            selected.toggle(); return true;
        }
        int rows = settingsRows(y - ROW);
        for (int i = 0; i < rows; i++) {
            int sy = y + 34 + i * 18;
            if (my >= sy && my < sy + 17 && mx >= x + W - 62 && mx < x + W - 9) {
                if (mx < x + W - 31) selected.getSettings().get(settingsScroll + i).adjust(-1);
                else selected.getSettings().get(settingsScroll + i).adjust(1);
                return true;
            }
        }
        int keyY = y + 34 + rows * 18;
        if (my >= keyY && my < keyY + 16) { binding = true; return true; }
        if (special() && my >= keyY + 17 && my < keyY + 34) { openEditor(); return true; }
        return true;
    }

    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (my >= height - 19 && my < height - 3) {
            if (mx >= width - 182 && mx < width - 98) {
                mc.displayGuiScreen(new PixelGuiEditor(this)); return;
            }
            if (mx >= width - 91 && mx < width - 7) {
                mc.displayGuiScreen(new HudEditor(this)); return;
            }
        }
        Category[] categories = Category.values();
        for (int col = 0; col < columns() && page + col < categories.length; col++) {
            int x = left(col);
            if (mx < x || mx >= x + W) continue;
            Category category = categories[page + col];
            List<Module> modules = GoldForge.MODULES.in(category);
            int rowY = TOP + HEADER + 1;
            for (int index = Math.max(0, offset(category)); index < modules.size()
                    && rowY + ROW <= bottom(); index++) {
                Module module = modules.get(index);
                if (my >= rowY && my < rowY + ROW) {
                    if (button == 0) module.toggle();
                    else if (button == 1) {
                        if (selected == module) { selected = null; binding = false; }
                        else {
                            selected = module; settingsScroll = 0; binding = false;
                            scroll.put(category, index); // Put the clicked row at the top so its editor fits.
                        }
                    }
                    return;
                }
                if (module == selected) {
                    int h = editorHeight(rowY);
                    if (settingsClick(x, rowY + ROW, h, mx, my)) return;
                    rowY += h;
                }
                rowY += ROW;
            }
        }
        if (my < 23) {
            if (mx < 105) page = Math.max(0, page - 1);
            else if (mx > width - 110) page = Math.min(maxPage(), page + 1);
        }
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        for (int col = 0; col < columns() && page + col < Category.values().length; col++) {
            int x = left(col);
            if (mx < x || mx >= x + W) continue;
            Category category = Category.values()[page + col];
            List<Module> modules = GoldForge.MODULES.in(category);
            int rowY = TOP + HEADER + 1;
            for (int i = Math.max(0, offset(category)); i < modules.size()
                    && rowY + ROW <= bottom(); i++) {
                if (modules.get(i) == selected) {
                    int h = editorHeight(rowY);
                    if (my >= rowY + ROW && my < rowY + ROW + h) {
                        settingsScroll = Math.max(0, Math.min(
                                Math.max(0, selected.getSettings().size() - settingsRows(rowY)),
                                settingsScroll + (wheel < 0 ? 1 : -1)));
                        return;
                    }
                    rowY += h;
                }
                rowY += ROW;
            }
            int max = Math.max(0, modules.size() - 1);
            scroll.put(category, Math.max(0, Math.min(max,
                    offset(category) + (wheel < 0 ? 2 : -2))));
            return;
        }
        page = Math.max(0, Math.min(maxPage(), page + (wheel < 0 ? 1 : -1)));
    }

    @Override protected void keyTyped(char ch, int key) throws IOException {
        if (binding && selected != null) {
            selected.setKey(key == Keyboard.KEY_ESCAPE ? 0 : key);
            binding = false; return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (selected != null) { selected = null; return; }
            mc.displayGuiScreen(parent); return;
        }
        if (key == Keyboard.KEY_LEFT) page = Math.max(0, page - 1);
        else if (key == Keyboard.KEY_RIGHT) page = Math.min(maxPage(), page + 1);
        super.keyTyped(ch, key);
    }
    @Override public void onGuiClosed() { ConfigManager.save(); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void drawDefaultBackground() {}
}

