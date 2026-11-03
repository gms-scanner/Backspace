package dev.goldforge.gui;

import cc.polyfrost.oneconfig.internal.gui.BlurHandler;
import cc.polyfrost.oneconfig.utils.gui.BlurScreen;
import dev.goldforge.ConfigManager;
import dev.goldforge.GoldForge;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.module.ModuleSetting;
import dev.goldforge.module.impl.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Content-sized category windows. Every expanded module owns space in the list. */
public final class PixelClickGui extends GuiScreen implements BlurScreen {
    private static final int W = 136, GAP = 7, ROW = 17, TOP = 30, HEADER = 19, FOOT = 27;
    private final GuiScreen parent;
    private final EnumMap<Category, Integer> scroll = new EnumMap<Category, Integer>(Category.class);
    private final Set<Module> expanded = Collections.newSetFromMap(new IdentityHashMap<Module, Boolean>());
    private final IdentityHashMap<Module, Float> animated = new IdentityHashMap<Module, Float>();
    private final IdentityHashMap<Object, Marquee> nameMarquees = new IdentityHashMap<Object, Marquee>();
    private final IdentityHashMap<Object, Marquee> descriptionMarquees = new IdentityHashMap<Object, Marquee>();
    private final IdentityHashMap<ModuleSetting, Float> sliderHover = new IdentityHashMap<ModuleSetting, Float>();
    private int page;
    private Module binding;
    private ModuleSetting activeSlider;
    private int activeSliderX, activeSliderWidth;
    private float frameSeconds;
    private long lastFrame;
    private static final int SEARCH_W = 174, SEARCH_H = 52, SEARCH_TAB_W = 76, SEARCH_TAB_H = 18;
    private GuiTextField searchField;
    private boolean searchOpen = true, searchDragging;
    private int searchX = -1, searchY = -1, dragX, dragY;
    private float searchDrawX = Float.NaN, searchDrawY = Float.NaN;
    private float searchExpansion = 1F;
    private long searchChangedAt = System.nanoTime();

    private static final class Marquee {
        float offset, timer;
        int phase;
    }

    public PixelClickGui() { this(null); }
    public PixelClickGui(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        BlurHandler.INSTANCE.reloadBlur(this);
        if (searchX < 0) searchX = Math.max(8, width - SEARCH_W - 10);
        if (searchY < 0) searchY = Math.max(TOP + HEADER + 8, height - SEARCH_H - 46);
        searchX = Math.max(2, Math.min(searchX, width - SEARCH_W - 2));
        searchY = Math.max(TOP + HEADER + 4, Math.min(searchY, height - SEARCH_H - 24));
        String old = searchField == null ? "" : searchField.getText();
        searchField = new GuiTextField(91, fontRenderer, 0, 0, SEARCH_W - 18, 16);
        searchField.setMaxStringLength(80);
        searchField.setText(old);
        searchField.setEnableBackgroundDrawing(false);
    }
    @Override public boolean hasBackgroundBlur() { return true; }

    private int columns() { return Math.max(1, (width - 16 + GAP) / (W + GAP)); }
    private int maxPage() { return Math.max(0, Category.values().length - columns()); }
    private int left(int column) { return 8 + column * (W + GAP); }
    private int bodyTop() { return TOP + HEADER; }
    private int maxBody(int x) {
        int bottom = height - FOOT;
        int sx = Math.round(searchDrawX), sy = Math.round(searchDrawY);
        int sw = Math.round(SEARCH_TAB_W + (SEARCH_W - SEARCH_TAB_W) * searchExpansion);
        if (!Float.isNaN(searchDrawX) && x < sx + sw + 4 && x + W > sx - 4)
            bottom = Math.min(bottom, sy - 5);
        return Math.max(0, bottom - bodyTop());
    }
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
    private int backgroundAlpha() {
        return Math.round(255F * (100 - GuiPreferences.pixelTransparency) / 100F);
    }
    private int surface() { return (backgroundAlpha() << 24) | 0x141515; }
    private void box(int x, int y, int w, int h, int fill) {
        if (h <= 0) return;
        Gui.drawRect(x, y, x + w, y + h, edge());
        if (w > 2 && h > 2) Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }
    private void text(String value, int x, int y, int color) {
        fontRenderer.drawString(value, x, y, color);
    }
    private int[] clipText(int x, int y, int w, int h) {
        IntBuffer old = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, old);
        int scale = new ScaledResolution(mc).getScaleFactor();
        int lx = x * scale, ly = mc.displayHeight - (y + h) * scale;
        int rx = lx + w * scale, ty = ly + h * scale;
        lx = Math.max(lx, old.get(0));
        ly = Math.max(ly, old.get(1));
        rx = Math.min(rx, old.get(0) + old.get(2));
        ty = Math.min(ty, old.get(1) + old.get(3));
        GL11.glScissor(lx, ly, Math.max(0, rx - lx), Math.max(0, ty - ly));
        return new int[] {old.get(0), old.get(1), old.get(2), old.get(3)};
    }
    private void marquee(Object key, IdentityHashMap<Object, Marquee> states,
                         String value, int x, int y, int available, boolean hovered, int color) {
        int overflow = fontRenderer.getStringWidth(value) - available;
        if (overflow <= 0) { text(value, x, y, color); return; }
        Marquee state = states.get(key);
        if (state == null) { state = new Marquee(); states.put(key, state); }
        float dt = frameSeconds;
        if (!hovered) {
            state.phase = 0; state.timer = 0;
            state.offset *= (float) Math.exp(-11F * dt);
            if (state.offset < 0.5F) state.offset = 0;
        } else if (state.phase == 0) {
            state.timer += dt;
            if (state.timer >= hoverDelay(value)) { state.phase = 1; state.timer = 0; }
        } else if (state.phase == 1) {
            state.offset += (overflow - state.offset) * (1F - (float) Math.exp((float)(-2.8F * dt / wordFactor(value))));
            if (overflow - state.offset < 0.5F) { state.offset = overflow; state.phase = 2; state.timer = 0; }
        } else if (state.phase == 2) {
            state.timer += dt;
            if (state.timer >= 0.8F) state.phase = 3;
        } else {
            state.offset *= (float) Math.exp(-5F * dt);
            if (state.offset < 0.5F) { state.offset = 0; state.phase = 0; state.timer = 0; }
        }
        int[] old = clipText(x, y, available, 10);
        text(value, x - Math.round(state.offset), y, color);
        GL11.glScissor(old[0], old[1], old[2], old[3]);
    }
    private static int words(String value) { String trimmed=value.trim();return trimmed.isEmpty()?1:trimmed.split("\\s+").length; }
    private static float wordFactor(String value) { return Math.min(5F,1F+Math.max(0,words(value)-1)*.22F); }
    private static float hoverDelay(String value) { return Math.min(1.8F,.45F+Math.max(0,words(value)-1)*.12F); }
    private int scroll(Category category) {
        Integer value = scroll.get(category);
        return value == null ? 0 : value;
    }
    private int panelTarget(Module module) {
        return 52 + module.getSettings().size() * 18 + (special(module) ? 17 : 0);
    }
    private int panelHeight(Module module) {
        Float value = animated.get(module);
        return value == null ? 0 : Math.max(0, Math.round(value));
    }
    private int contentHeight(List<Module> modules) {
        int result = 2;
        for (Module module : modules) result += ROW + panelHeight(module);
        return result;
    }
    private int bodyHeight(List<Module> modules, int x) {
        return Math.min(maxBody(x), contentHeight(modules));
    }
    private int maxScroll(List<Module> modules, int x) {
        return Math.max(0, contentHeight(modules) - bodyHeight(modules, x));
    }
    private void updateAnimations(float partial) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.05F, (now - lastFrame) / 1000000000F);
        frameSeconds = dt;
        lastFrame = now;
        int tabX = Math.max(2, width - SEARCH_TAB_W - 9);
        int tabY = Math.max(TOP + HEADER + 4, height - SEARCH_TAB_H - 30);
        if (Float.isNaN(searchDrawX)) { searchDrawX = searchOpen ? searchX : tabX; searchDrawY = searchOpen ? searchY : tabY; }
        float searchEase = 1F - (float) Math.exp(-20F * dt);
        searchDrawX += ((searchOpen ? searchX : tabX) - searchDrawX) * searchEase;
        searchDrawY += ((searchOpen ? searchY : tabY) - searchDrawY) * searchEase;
        searchExpansion += ((searchOpen ? 1F : 0F) - searchExpansion) * searchEase;
        if (Math.abs(searchExpansion - (searchOpen ? 1F : 0F)) < .005F)
            searchExpansion = searchOpen ? 1F : 0F;
        float ease = 1F - (float) Math.exp(-14F * dt);
        for (Category category : Category.values()) {
            for (Module module : GoldForge.MODULES.in(category)) {
                float goal = expanded.contains(module) ? panelTarget(module) : 0;
                Float old = animated.get(module);
                float next = (old == null ? 0 : old) + (goal - (old == null ? 0 : old)) * ease;
                if (Math.abs(goal - next) < 0.5F) next = goal;
                animated.put(module, next);
            }
        }
    }
    private void clip(int x, int y, int w, int h) {
        int scale = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, mc.displayHeight - (y + h) * scale,
                Math.max(0, w * scale), Math.max(0, h * scale));
    }

    @Override public void drawScreen(int mx, int my, float partial) {
        ScaledResolution resolution = new ScaledResolution(mc);
        if (width != resolution.getScaledWidth() || height != resolution.getScaledHeight())
            setWorldAndResolution(mc, resolution.getScaledWidth(), resolution.getScaledHeight());
        updateAnimations(partial);
        // A previous renderer can leave a scissor rectangle active. Own the
        // clipping state for this frame so headers and page controls stay visible.
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        BrandMark.draw(8, 5, 20);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        // The world is left untouched; the opacity preference controls the actual window fill.
        text("BACKSPACE", 34, 10, accent());
        text("<  CATEGORIES  >", Math.max(8, width - 100), 8, 0xFFE0E0D0);
        page = Math.min(page, maxPage());
        Category[] categories = Category.values();
        for (int col = 0; col < columns() && page + col < categories.length; col++)
            drawColumn(categories[page + col], left(col), mx, my);
        drawSearch(mx, my);
        text("LMB TOGGLE  |  RMB SETTINGS  |  WHEEL SCROLL", 8, height - 13, 0xFFEAD993);
        box(width - 182, height - 19, 84, 16, accent());
        text("GUI EDITOR", width - 176, height - 15, 0xFF101010);
        box(width - 91, height - 19, 84, 16, accent());
        text("HUD EDITOR", width - 85, height - 15, 0xFF101010);
        GL11.glPopAttrib();
    }

    private void drawColumn(Category category, int x, int mx, int my) {
        List<Module> modules = GoldForge.MODULES.in(category);
        int body = bodyHeight(modules, x);
        int max = maxScroll(modules, x);
        int offset = Math.max(0, Math.min(scroll(category), max));
        scroll.put(category, offset);
        box(x, TOP, W, HEADER, accent());
        text(category.name(), x + 5, TOP + 5, 0xFF101010);
        text(Integer.toString(modules.size()), x + W - 18, TOP + 5, 0xFF101010);
        if (body <= 0) return;
        GlStateManager.disableAlpha();
        box(x, bodyTop(), W, body, surface());
        GlStateManager.enableAlpha();
        clip(x + 1, bodyTop() + 1, W - 2, Math.max(0, body - 2));
        int y = bodyTop() + 1 - offset;
        for (Module module : modules) {
            if (y + ROW > bodyTop() && y < bodyTop() + body) {
                boolean hover = mx >= x && mx < x + W && my >= y && my < y + ROW;
                if (searchMatches(module)) {
                    double elapsed = (System.nanoTime() - searchChangedAt) / 1000000000D;
                    int alpha = 35 + (int) (115D * (0.5D + 0.5D * Math.sin(elapsed * 5D - Math.PI / 2D)));
                    Gui.drawRect(x + 1, y, x + W - 1, y + ROW, (alpha << 24) | 0xE32626);
                }
                if (hover) Gui.drawRect(x + 1, y, x + W - 1, y + ROW,
                        (backgroundAlpha() * 0x55 / 255 << 24) | 0x6B591C);
                if (module.enabled) Gui.drawRect(x + 1, y, x + 3, y + ROW, accent());
                marquee(module, nameMarquees, module.name, x + 6, y + 4, W - 27, hover,
                        module.enabled ? accent() : 0xFFE5E5D6);
                circle(x + W - 12, y + 8, module.enabled);
            }
            y += ROW;
            int h = panelHeight(module);
            if (h > 0 && y + h > bodyTop() && y < bodyTop() + body)
                drawSettings(module, x, y, h, body, mx, my);
            y += h;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (max > 0) {
            int track = Math.max(8, body - 4);
            int thumb = Math.max(8, track * body / contentHeight(modules));
            int pos = max == 0 ? 0 : (track - thumb) * offset / max;
            Gui.drawRect(x + W - 3, bodyTop() + 2 + pos,
                    x + W - 1, bodyTop() + 2 + pos + thumb, accent());
        }
    }

    private boolean searchMatches(Module module) {
        if (searchField == null) return false;
        String query = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        return !query.isEmpty() && module.name.toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    private void drawSearch(int mx, int my) {
        int x = Math.round(searchDrawX), y = Math.round(searchDrawY);
        int sw = Math.round(SEARCH_TAB_W + (SEARCH_W - SEARCH_TAB_W) * searchExpansion);
        int sh = Math.round(SEARCH_TAB_H + (SEARCH_H - SEARCH_TAB_H) * searchExpansion);
        if (searchExpansion < .8F) {
            boolean hover = mx >= x - 4 && mx < x + SEARCH_TAB_W && my >= y && my < y + SEARCH_TAB_H;
            int lift = hover ? 4 : 0;
            box(x - lift, y, sw + lift, sh, accent());
            text("SEARCH  >", x + 6 - lift, y + 5, 0xFF101010);
            return;
        }
        box(x, y, sw, sh, surface());
        Gui.drawRect(x, y, x + sw, y + 17, accent());
        text("SEARCH", x + 6, y + 4, 0xFF101010);
        text("x", x + sw - 12, y + 4, 0xFF101010);
        if (searchExpansion < .99F) return;
        Gui.drawRect(x + 7, y + 23, x + sw - 7, y + 44, 0xCC25251D);
        Gui.drawRect(x + 8, y + 43, x + sw - 8, y + 44, accent());
        searchField.x = x + 10;
        searchField.y = y + 29;
        searchField.width = SEARCH_W - 20;
        searchField.drawTextBox();
        if (searchField.getText().isEmpty() && !searchField.isFocused())
            text("Find module...", x + 11, y + 29, 0xFFACA78A);
    }

    private void circle(int cx, int cy, boolean enabled) {
        int[][] pixels = {{-2,-4},{-1,-4},{0,-4},{1,-4},{-3,-3},{2,-3},{-4,-2},{3,-2},
                {-4,-1},{3,-1},{-4,0},{3,0},{-3,1},{2,1},{-2,2},{-1,2},{0,2},{1,2}};
        for (int[] p : pixels) Gui.drawRect(cx + p[0], cy + p[1], cx + p[0] + 1, cy + p[1] + 1, accent());
        if (enabled) Gui.drawRect(cx - 2, cy - 2, cx + 2, cy + 1, accent());
    }

    private void drawSettings(Module module, int x, int y, int h, int body, int mx, int my) {
        int top = Math.max(y, bodyTop() + 1);
        int end = Math.min(y + h, bodyTop() + body - 1);
        if (end <= top) return;
        clip(x + 4, top, W - 8, end - top);
        // The category body supplies the only translucent fill. A second fill here
        // would compound its alpha and make open settings appear more opaque.
        Gui.drawRect(x + 4, y + 1, x + 6, y + h - 1, accent());
        marquee(module, descriptionMarquees, module.description, x + 9, y + 4, W - 19,
                mx >= x + 9 && mx < x + W - 9 && my >= y && my < y + 15, 0xFFE4E4D8);
        Gui.drawRect(x + 9, y + 16, x + W - 9, y + 30,
                module.enabled ? accent() : 0xB0494944);
        text(module.enabled ? "ENABLED" : "DISABLED", x + 13, y + 19,
                module.enabled ? 0xFF101010 : 0xFFF1F1E4);
        List<ModuleSetting> options = module.getSettings();
        for (int i = 0; i < options.size(); i++) {
            ModuleSetting option = options.get(i);
            int sy = y + 34 + i * 18;
            boolean hover = mx >= x + 9 && mx < x + W - 9 && my >= sy && my < sy + 17;
            Float oldHover = sliderHover.get(option);
            float glow = (oldHover == null ? 0 : oldHover);
            glow += ((hover || activeSlider == option ? 1F : 0F) - glow)
                    * (1F - (float) Math.exp(-12F * frameSeconds));
            sliderHover.put(option, glow);
            String value = option.formattedValue();
            int valueWidth = fontRenderer.getStringWidth(value);
            boolean toggle=option.isToggle();
            int nameWidth = toggle ? W - 94 : Math.max(18, W - 33 - valueWidth);
            marquee(option, nameMarquees, option.name, x + 12, sy + 1, nameWidth,
                    hover, 0xFFE8E7D9);
            if(toggle){
                int switchX=x+W-45;Gui.drawRect(switchX,sy+3,switchX+31,sy+14,option.isEnabled()?accent():0xFF41413A);
                Gui.drawRect(switchX+(option.isEnabled()?19:3),sy+5,switchX+(option.isEnabled()?27:11),sy+12,0xFFFFF8D0);
                text(option.isEnabled()?"ON":"OFF",x+W-68,sy+2,option.isEnabled()?accent():0xFFB9B9A9);
            }else{
                text(value, x + W - 12 - valueWidth, sy + 1, accent());
                int trackX = x + 12, trackWidth = W - 24;
                Gui.drawRect(trackX, sy + 13, trackX + trackWidth, sy + 17, 0xFF292923);
                int filled = Math.round(trackWidth * (float) option.fraction());
                Gui.drawRect(trackX, sy + 13, trackX + filled, sy + 17, accent());
                int knob = trackX + filled;
                Gui.drawRect(Math.max(trackX, knob - 2), sy + 11, Math.min(trackX + trackWidth, knob + 2), sy + 18, accent());
                if (glow > 0.02F) Gui.drawRect(trackX, sy + 10, trackX + trackWidth, sy + 11,
                        (Math.round(glow * 120F) << 24) | (accent() & 0xFFFFFF));
            }
        }
        int keyY = y + 34 + options.size() * 18;
        Gui.drawRect(x + 9, keyY, x + W - 9, keyY + 16, 0xA0423C23);
        text(binding == module ? "PRESS A KEY..." : "KEY: " + (module.key == 0 ? "NONE" :
                Keyboard.getKeyName(module.key)), x + 12, keyY + 4, accent());
        if (special(module)) {
            Gui.drawRect(x+9,keyY+18,x+W-9,keyY+33,0xA0423C23);
            text("CONFIGURE", x + 13, keyY + 21, accent());
        }
        clip(x + 1, bodyTop() + 1, W - 2, Math.max(0, body - 2));
    }

    private boolean special(Module module) {
        return module instanceof BlockESP || module instanceof DIESP
                || module instanceof AutoSign || module instanceof Hands || ModuleTextEditor.supports(module);
    }
    private void openEditor(Module module) {
        if (module instanceof BlockESP) mc.displayGuiScreen(new BlockSelectGui(this, (BlockESP) module));
        else if (module instanceof DIESP) mc.displayGuiScreen(new ItemSelectGui(this, (DIESP) module));
        else if (module instanceof AutoSign) mc.displayGuiScreen(new SignTextGui(this, (AutoSign) module));
        else if (module instanceof Hands) ((Hands) module).openEditor();
        else if(ModuleTextEditor.supports(module))mc.displayGuiScreen(new ModuleTextEditor(this,module));
    }
    private void settingsClick(Module module, int x, int y, int h, int mx, int my) {
        if (my >= y + 16 && my < y + 30 && mx >= x + 9 && mx < x + W - 9) {
            module.toggle(); return;
        }
        List<ModuleSetting> options = module.getSettings();
        for (int i = 0; i < options.size(); i++) {
            int sy = y + 34 + i * 18;
            ModuleSetting option=options.get(i);
            if(option.isToggle()&&my>=sy&&my<sy+18&&mx>=x+9&&mx<x+W-9){option.toggle();return;}
            if (!option.isToggle() && my >= sy + 10 && my < sy + 18 && mx >= x + 12 && mx <= x + W - 12) {
                activeSlider = option;
                activeSliderX = x + 12;
                activeSliderWidth = W - 24;
                activeSlider.setFraction((mx - activeSliderX) / (double) activeSliderWidth);
                return;
            }
        }
        int keyY = y + 34 + options.size() * 18;
        if (my >= keyY && my < keyY + 16) { binding = module; return; }
        if (special(module) && my >= keyY + 17 && my < keyY + 34) openEditor(module);
    }

    @Override protected void mouseClickMove(int mx, int my, int button, long time) {
        if (button == 0 && searchDragging) {
            searchX = Math.max(2, Math.min(mx - dragX, width - SEARCH_W - 2));
            searchY = Math.max(TOP + HEADER + 4, Math.min(my - dragY, height - SEARCH_H - 24));
            searchDrawX = searchX; searchDrawY = searchY;
            return;
        }
        if (button == 0 && activeSlider != null)
            activeSlider.setFraction((mx - activeSliderX) / (double) activeSliderWidth);
    }
    @Override protected void mouseReleased(int mx, int my, int button) {
        if (button == 0) searchDragging = false;
        if (button == 0 && activeSlider != null) {
            activeSlider = null;
            ConfigManager.save();
        }
    }

    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        int sx = Math.round(searchDrawX), sy = Math.round(searchDrawY);
        if (button == 0 && !searchOpen && mx >= sx - 5 && mx < sx + SEARCH_TAB_W && my >= sy && my < sy + SEARCH_TAB_H) {
            searchOpen = true; return;
        }
        if (searchOpen && mx >= sx && mx < sx + SEARCH_W && my >= sy && my < sy + SEARCH_H) {
            if (button == 0 && my < sy + 17) {
                if (mx >= sx + SEARCH_W - 18) { searchOpen = false; searchField.setFocused(false); }
                else { searchDragging = true; dragX = mx - sx; dragY = my - sy; }
            } else searchField.mouseClicked(mx, my, button);
            return;
        }
        if (searchField != null) searchField.setFocused(false);
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
            int body = bodyHeight(modules, x);
            if (my < bodyTop() + 1 || my >= bodyTop() + body - 1) continue;
            int y = bodyTop() + 1 - Math.min(scroll(category), maxScroll(modules, x));
            for (Module module : modules) {
                if (my >= y && my < y + ROW) {
                    if (button == 0) module.toggle();
                    else if (button == 1) {
                        if (!expanded.add(module)) { expanded.remove(module); if (binding == module) binding = null; }
                    }
                    return;
                }
                y += ROW;
                int h = panelHeight(module);
                if (my >= y && my < y + h) {
                    if (button == 0) settingsClick(module, x, y, h, mx, my);
                    return;
                }
                y += h;
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
            if (my >= bodyTop() && my < bodyTop() + bodyHeight(modules, x)) {
                scroll.put(category, Math.max(0, Math.min(maxScroll(modules, x),
                        scroll(category) + (wheel < 0 ? ROW * 2 : -ROW * 2))));
                return;
            }
        }
        page = Math.max(0, Math.min(maxPage(), page + (wheel < 0 ? 1 : -1)));
    }

    @Override protected void keyTyped(char ch, int key) throws IOException {
        if (searchOpen && searchField != null && searchField.isFocused()) {
            String before = searchField.getText();
            searchField.textboxKeyTyped(ch, key);
            if (!before.equals(searchField.getText())) searchChangedAt = System.nanoTime();
            if (key == Keyboard.KEY_ESCAPE) searchField.setFocused(false);
            return;
        }
        if (binding != null) {
            binding.setKey(key == Keyboard.KEY_ESCAPE ? 0 : key);
            binding = null; return;
        }
        if (key == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        if (key == Keyboard.KEY_LEFT) page = Math.max(0, page - 1);
        else if (key == Keyboard.KEY_RIGHT) page = Math.min(maxPage(), page + 1);
        else super.keyTyped(ch, key);
    }
    @Override public void onGuiClosed() { BlurHandler.INSTANCE.reloadBlur(null); ConfigManager.save(); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void drawDefaultBackground() {}
}

