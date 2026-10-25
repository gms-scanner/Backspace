package dev.goldforge.gui;

import dev.goldforge.module.Category;
import java.util.Properties;

/** Persistent ClickGUI presentation preferences. */
public final class GuiPreferences {
    public static int layout = 1;
    public static int theme = 0; // gold, red, mint, blue
    public static int pixelOpacity = 160;
    public static final int[] panelX = new int[Category.values().length];
    public static final int[] panelY = new int[Category.values().length];
    public static final boolean[] collapsed = new boolean[Category.values().length];
    static { for (int i = 0; i < panelX.length; i++) { panelX[i] = -1; panelY[i] = -1; } }
    private GuiPreferences() {}

    public static void load(Properties values) {
        layout = clamp(read(values, "gui.layout", layout), 0, 1);
        theme = clamp(read(values, "gui.theme", theme), 0, 3);
        pixelOpacity = clamp(read(values, "gui.pixel.opacity", pixelOpacity), 32, 255);
        for (Category category : Category.values()) {
            String prefix = "gui.panel." + category.name().toLowerCase(java.util.Locale.ROOT) + ".";
            int index = category.ordinal();
            panelX[index] = read(values, prefix + "x", -1);
            panelY[index] = read(values, prefix + "y", -1);
            collapsed[index] = Boolean.parseBoolean(values.getProperty(prefix + "collapsed", "false"));
        }
    }
    public static void save(Properties values) {
        values.setProperty("gui.layout", Integer.toString(clamp(layout, 0, 1)));
        values.setProperty("gui.theme", Integer.toString(clamp(theme, 0, 3)));
        values.setProperty("gui.pixel.opacity", Integer.toString(clamp(pixelOpacity, 32, 255)));
        for (Category category : Category.values()) {
            String prefix = "gui.panel." + category.name().toLowerCase(java.util.Locale.ROOT) + ".";
            int index = category.ordinal();
            values.setProperty(prefix + "x", Integer.toString(panelX[index]));
            values.setProperty(prefix + "y", Integer.toString(panelY[index]));
            values.setProperty(prefix + "collapsed", Boolean.toString(collapsed[index]));
        }
    }
    private static int read(Properties values, String key, int fallback) {
        try { return Integer.parseInt(values.getProperty(key)); }
        catch (Exception ignored) { return fallback; }
    }
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

