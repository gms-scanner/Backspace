package dev.goldforge.gui;

import cc.polyfrost.oneconfig.libs.universal.UKeyboard;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.gui.OneUIScreen;
import dev.goldforge.ConfigManager;
import dev.goldforge.GoldForge;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.module.ModuleSetting;
import dev.goldforge.module.impl.AutoSign;
import dev.goldforge.module.impl.BlockESP;
import dev.goldforge.module.impl.CustomChat;
import dev.goldforge.module.impl.KillFX;
import dev.goldforge.module.impl.ShaderFX;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Backspace's OneConfig/NanoVG module browser.  NanoVG handles all panel,
 * text, shadow and clipping-sensitive work instead of the old Gui.drawRect
 * and tessellator path, which is why this screen stays anti-aliased under 1.12.
 */
public final class ModernClickGui extends OneUIScreen {
    private static final int FRAME = 0xE80B0D10;
    private static final int SURFACE = 0xE8111419;
    private static final int SURFACE_HOVER = 0xF0171B21;
    private static final int CARD = 0xEE171B20;
    private static final int CARD_HOVER = 0xF01E242B;
    private static final int TEXT = 0xFFF4F5F6;
    private static final int MUTED = 0xFF9298A3;
    private static final int DIM = 0xFF656C75;
    private static final int YELLOW = 0xFFFFCC22;
    private static final int YELLOW_DARK = 0xFF6B5610;
    private static final int STROKE = 0xFF252A31;
    private static final float OUTER_INSET = 22F;
    private static final float PANEL_INSET = 14F;
    private static final float GRID_GAP = 10F;
    private static final float TOGGLE_WIDTH = 34F;
    private static final float TOGGLE_RIGHT_GUTTER = 12F;
    // Layout values in this screen are top-edge coordinates. The text wrapper
    // below translates them to OneConfig's vertically-centred text API.
    private static final float HEADER_TITLE_TOP = 12F;
    private static final float HEADER_TITLE_SIZE = 22F;
    private static final float HEADER_SUBTITLE_GAP = 29F;
    private static final float HEADER_SUBTITLE_SIZE = 10F;
    private static final float HEADER_CONTROL_HEIGHT = 34F;

    private final GuiScreen parent;
    private Category selectedCategory = Category.COMBAT;
    private Module selectedModule;
    private String search = "";
    private boolean searchFocused;
    private boolean awaitingKeyBind;
    private int moduleScroll;
    private int settingsScroll;
    private long openedAt;

    public ModernClickGui() { this(null); }

    public ModernClickGui(GuiScreen parent) {
        // OneUIScreen must use Minecraft's scaled coordinate system.  The old
        // false path drew scaled GuiScreen coordinates into the physical
        // framebuffer, which made the entire browser appear half-sized.
        super(true);
        this.parent = parent;
    }

    @Override public void initScreen(int screenWidth, int screenHeight) {
        openedAt = System.currentTimeMillis();
    }

    @Override public boolean hasBackgroundBlur() { return true; }

    @Override public void draw(long vg, float partialTicks, InputHandler input) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        float alpha = Math.min(1F, (System.currentTimeMillis() - openedAt) / 170F);
        // Keep the browser substantial without turning it into a full-screen
        // overlay at lower Minecraft GUI scales.
        float frameW = Math.min(810F, width - 70F);
        float frameH = Math.min(470F, height - 50F);
        float frameX = (width - frameW) * .5F;
        float frameY = (height - frameH) * .5F;

        // Leave the world visible. This shader paints only a soft gold falloff
        // around the rounded frame; it never writes a black full-screen canvas.
        FrameGlowShader.draw(0F, 0F, width, height, frameX, frameY, frameW, frameH, 20F,
                YELLOW, (System.currentTimeMillis() - openedAt) / 1000F);
        // A light transparent wash keeps bright world geometry from competing
        // with the controls while OneConfig supplies the actual background blur.
        nvg.drawRect(vg, 0F, 0F, width, height, 0x2404070A);
        // The GLSL pass above owns the gold border. Keep the surface neutral so
        // there is no second hard outline or yellow stripe competing with it.
        nvg.drawDropShadow(vg, frameX, frameY, frameW, frameH, 22F, 0F, 8F);
        nvg.drawRoundedRect(vg, frameX, frameY, frameW, frameH, FRAME, 20F);

        drawBrand(vg, frameX, frameY);
        drawHeader(vg, input, frameX, frameY, frameW);

        float contentY = frameY + 78F;
        float contentH = frameH - 106F;
        float sidebarW = Math.min(168F, frameW * .18F);
        float detailW = Math.min(224F, frameW * .245F);
        float sidebarX = frameX + OUTER_INSET;
        float modulesX = sidebarX + sidebarW + OUTER_INSET;
        float detailX = frameX + frameW - detailW - OUTER_INSET;
        float modulesW = detailX - modulesX - 18F;

        drawSidebar(vg, input, sidebarX, contentY, sidebarW, contentH);
        drawModules(vg, input, modulesX, contentY, modulesW, contentH);
        drawInspector(vg, input, detailX, contentY, detailW, contentH);

        float footerY = lineY(frameY + frameH - 43F, 18F, 11F);
        text(vg, "RIGHT SHIFT  •  ESC CLOSE", frameX + OUTER_INSET, footerY, 11F, 0xFFB6BDC7);
        textRight(vg, "CLIENT READY", frameX + frameW - OUTER_INSET, footerY, 11F, MUTED);
    }

    private void drawBrand(long vg, float x, float y) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        // A tight viewbox avoids the hidden padding in the old 512px logo SVG.
        // Its center matches the wordmark/subtitle block and the header controls.
        nvg.drawSvg(vg, "/assets/goldforge/icons/backspace-mark.svg", x + 21F, y + 14F, 36F, 36F, ModernClickGui.class);
        float titleY = y + HEADER_TITLE_TOP;
        float titleX = x + 69F;
        // Text metrics change with GUI scale and NanoVG's active font. Advance
        // the yellow half by the actual width of "back" instead of a guessed
        // pixel offset, which caused the k/s glyphs to overlap.
        float spaceX = titleX + nvg.getTextWidth(vg, "back", HEADER_TITLE_SIZE, Fonts.REGULAR);
        text(vg, "back", titleX, titleY, HEADER_TITLE_SIZE, TEXT);
        text(vg, "space", spaceX, titleY, HEADER_TITLE_SIZE, YELLOW);
        text(vg, "DEV 3.0 BETA", titleX, titleY + HEADER_SUBTITLE_GAP, HEADER_SUBTITLE_SIZE, MUTED);
    }

    private void drawHeader(long vg, InputHandler input, float x, float y, float width) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        // Center the controls against the visible brand block, not against
        // the gap between its two text rows. This keeps its top/bottom margins
        // symmetric with the wordmark, subtitle and compact logo mark.
        float brandTop = y + HEADER_TITLE_TOP;
        float brandBottom = brandTop + HEADER_SUBTITLE_GAP + HEADER_SUBTITLE_SIZE;
        float brandCenterY = (brandTop + brandBottom) * .5F;
        float controlY = brandCenterY - HEADER_CONTROL_HEIGHT * .5F;
        float closeX = x + width - OUTER_INSET - HEADER_CONTROL_HEIGHT;
        float hudX = closeX - 8F - 54F;
        float searchW = Math.min(242F, width * .25F);
        float searchX = hudX - 14F - searchW;
        float searchY = controlY;
        boolean hover = input.isAreaHovered(searchX, searchY, searchW, 34F);
        nvg.drawRoundedRect(vg, searchX, searchY, searchW, HEADER_CONTROL_HEIGHT, hover || searchFocused ? CARD_HOVER : SURFACE, 17F);
        nvg.drawHollowRoundRect(vg, searchX, searchY, searchW, HEADER_CONTROL_HEIGHT, searchFocused ? 0xFF765F11 : STROKE, 17F, 1F);
        svg(vg, "search", searchX + 11F, searchY + 10F, 14F, MUTED);
        text(vg, search.isEmpty() ? "Search modules…" : trim(vg, search, searchW - 58F, 13F), searchX + 34F, lineY(searchY, HEADER_CONTROL_HEIGHT, 13F), 13F,
                search.isEmpty() ? DIM : TEXT);
        if (!search.isEmpty()) textRight(vg, "×", searchX + searchW - 13F, searchY + 8F, 16F, MUTED);
        if (input.isAreaClicked(searchX, searchY, searchW, HEADER_CONTROL_HEIGHT)) {
            if (!search.isEmpty() && input.mouseX() >= searchX + searchW - 34F) search = "";
            searchFocused = true;
            moduleScroll = 0;
        }

        pill(vg, input, hudX, controlY, 54F, HEADER_CONTROL_HEIGHT, "HUD", "layout-dashboard", false);
        if (input.isAreaClicked(hudX, controlY, 54F, HEADER_CONTROL_HEIGHT)) {
            mc.displayGuiScreen(new HudEditor(this));
        }
        iconButton(vg, input, closeX, controlY, HEADER_CONTROL_HEIGHT, "x");
        if (input.isAreaClicked(closeX, controlY, HEADER_CONTROL_HEIGHT, HEADER_CONTROL_HEIGHT)) mc.displayGuiScreen(parent);
        nvg.drawRect(vg, x + OUTER_INSET, y + 65F, width - OUTER_INSET * 2F, 1F, STROKE);
    }

    private void drawSidebar(long vg, InputHandler input, float x, float y, float w, float h) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        nvg.drawRoundedRect(vg, x, y, w, h, SURFACE, 16F);
        nvg.drawHollowRoundRect(vg, x, y, w, h, STROKE, 16F, 1F);
        pushClip(x + 1F, y + 1F, w - 2F, h - 2F);
        text(vg, "LIBRARY", x + 16F, lineY(y + 9F, 20F, 12F), 12F, MUTED);
        Category[] categories = Category.values();
        float rowY = y + 35F;
        float rowStep = (h - 41F) / categories.length;
        float rowH = Math.min(31F, rowStep - 3F);
        for (Category category : categories) {
            boolean active = category == selectedCategory;
            float rowX = x + 8F;
            float rowW = w - 16F;
            boolean hovered = input.isAreaHovered(rowX, rowY, rowW, rowH);
            if (active || hovered) {
                nvg.drawRoundedRect(vg, rowX, rowY, rowW, rowH, active ? 0xFF3D310B : CARD, 10F);
                if (active) nvg.drawRoundedRect(vg, rowX, rowY + 6F, 3F, rowH - 12F, YELLOW, 2F);
            }
            float iconX = rowX + 11F;
            float countRight = x + w - 16F;
            float labelX = iconX + 27F;
            float categorySize = fitTextSize(vg, category.name(), countRight - labelX - 12F, 11F, 9F);
            float labelY = lineY(rowY, rowH, categorySize);
            svg(vg, iconFor(category), iconX, rowY + (rowH - 17F) * .5F, 17F, 0xFFFFFFFF);
            text(vg, category.name(), labelX, labelY, categorySize, active ? YELLOW : TEXT);
            textRight(vg, Integer.toString(GoldForge.MODULES.in(category).size()), countRight, lineY(rowY, rowH, 11F), 11F, active ? YELLOW : DIM);
            if (input.isAreaClicked(rowX, rowY, rowW, rowH)) {
                selectedCategory = category;
                selectedModule = null;
                searchFocused = false;
                moduleScroll = 0;
                settingsScroll = 0;
            }
            rowY += rowStep;
        }
        popClip();
    }

    private void drawModules(long vg, InputHandler input, float x, float y, float w, float h) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        nvg.drawRoundedRect(vg, x, y, w, h, SURFACE, 16F);
        nvg.drawHollowRoundRect(vg, x, y, w, h, STROKE, 16F, 1F);
        pushClip(x + 1F, y + 1F, w - 2F, h - 2F);
        String title = search.isEmpty() ? selectedCategory.name() + " MODULES" : "SEARCH RESULTS";
        text(vg, title, x + 18F, lineY(y + 9F, 22F, 13F), 13F, TEXT);
        textRight(vg, Integer.toString(visibleModules().size()), x + w - 18F, lineY(y + 9F, 22F, 12F), 12F, MUTED);

        List<Module> modules = visibleModules();
        float cardW = (w - PANEL_INSET * 2F - GRID_GAP) * .5F;
        // Fit five rows whenever the screen can support them; two-column cards
        // remain readable while Combat no longer opens as a six-item viewport.
        float cardH = Math.max(50F, Math.min(64F, (h - 58F) / 5F - 8F));
        float rowStep = cardH + GRID_GAP;
        int visibleRows = Math.max(1, (int) ((h - 52F + GRID_GAP) / rowStep));
        int visibleCards = visibleRows * 2;
        int maxScroll = Math.max(0, modules.size() - visibleCards);
        moduleScroll = Math.max(0, Math.min(moduleScroll, maxScroll));
        for (int card = 0; card < visibleCards && moduleScroll + card < modules.size(); card++) {
            Module module = modules.get(moduleScroll + card);
            int column = card & 1;
            int row = card >> 1;
            float cardX = x + PANEL_INSET + column * (cardW + GRID_GAP);
            float cardY = y + 42F + row * rowStep;
            boolean hovered = input.isAreaHovered(cardX, cardY, cardW, cardH);
            boolean selected = module == selectedModule;
            nvg.drawRoundedRect(vg, cardX, cardY, cardW, cardH, selected ? 0xFF28200A : hovered ? CARD_HOVER : CARD, 12F);
            nvg.drawHollowRoundRect(vg, cardX, cardY, cardW, cardH, selected ? 0xFF806712 : STROKE, 12F, 1F);
            // Fixed icon cell plus one shared text column. Keeping both lines
            // on this column avoids the stepped header/description layout that
            // made cards look as though the icon was colliding with the text.
            nvg.drawRoundedRect(vg, cardX + 10F, cardY + 8F, 24F, 24F, 0xFF242A31, 8F);
            svg(vg, iconFor(module.category), cardX + 14F, cardY + 12F, 16F, 0xFFFFFFFF);
            float textX = cardX + 43F;
            float titleWidth = cardW - 43F - TOGGLE_WIDTH - TOGGLE_RIGHT_GUTTER - 8F;
            float titleSize = fitTextSize(vg, module.name, titleWidth, 13F, 11F);
            // Title shares the icon cell's vertical center rather than sitting
            // above it, which keeps every module header on one baseline.
            text(vg, trimWords(vg, module.name, titleWidth, titleSize), textX, lineY(cardY + 8F, 24F, titleSize), titleSize, module.enabled ? YELLOW : TEXT);
            drawDescription(vg, module.description, textX, cardY + 30F, cardW - (textX - cardX) - PANEL_INSET, cardH, MUTED);
            float toggleX = cardX + cardW - TOGGLE_RIGHT_GUTTER - TOGGLE_WIDTH;
            float toggleY = cardY + 8F + (24F - 18F) * .5F;
            drawToggle(vg, input, toggleX, toggleY, module.enabled);
            if (input.isAreaClicked(toggleX - 4F, cardY + 6F, TOGGLE_WIDTH + 8F, 28F)) {
                module.toggle();
            } else if (input.isAreaClicked(cardX, cardY, cardW, cardH)) {
                selectedModule = module;
                awaitingKeyBind = false;
                settingsScroll = 0;
                searchFocused = false;
            }
        }
        if (modules.size() > visibleCards) {
            float trackY = y + 42F;
            float trackH = h - 42F - PANEL_INSET;
            float thumbH = Math.max(24F, trackH * visibleCards / modules.size());
            float thumbY = trackY + (trackH - thumbH) * moduleScroll / Math.max(1, modules.size() - visibleCards);
            nvg.drawRoundedRect(vg, x + w - 7F, trackY, 3F, trackH, 0xFF252B32, 2F);
            nvg.drawRoundedRect(vg, x + w - 8F, thumbY, 5F, thumbH, YELLOW, 3F);
        }
        popClip();
    }

    private void drawInspector(long vg, InputHandler input, float x, float y, float w, float h) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        nvg.drawRoundedRect(vg, x, y, w, h, SURFACE, 16F);
        nvg.drawHollowRoundRect(vg, x, y, w, h, STROKE, 16F, 1F);
        pushClip(x + 1F, y + 1F, w - 2F, h - 2F);
        float infoX = x + 18F;
        float infoW = w - 36F;
        text(vg, "MODULE INFO", infoX, lineY(y + 9F, 20F, 12F), 12F, MUTED);
        if (selectedModule == null) {
            text(vg, "Choose a module", infoX, y + 58F, 16F, TEXT);
            text(vg, "Toggle a card, then select it", infoX, y + 89F, 12F, MUTED);
            text(vg, "to edit its settings here.", infoX, y + 106F, 12F, MUTED);
            text(vg, "QUICK ACCESS", infoX, y + 151F, 11F, YELLOW);
            action(vg, input, x + PANEL_INSET, y + 168F, w - PANEL_INSET * 2F, "MODULES", GoldForge.MODULES.all().size() + " AVAILABLE");
            action(vg, input, x + PANEL_INSET, y + 207F, w - PANEL_INSET * 2F, "HUD EDITOR", "OPEN");
            if (input.isAreaClicked(x + PANEL_INSET, y + 207F, w - PANEL_INSET * 2F, 31F)) mc.displayGuiScreen(new HudEditor(this));
            popClip();
            return;
        }

        text(vg, trim(vg, selectedModule.name, w - 72F, 17F), x + 18F, y + 48F, 17F, selectedModule.enabled ? YELLOW : TEXT);
        text(vg, selectedModule.category.name(), x + 18F, y + 72F, 11F, MUTED);
        drawToggle(vg, input, x + w - 58F, y + 37F, selectedModule.enabled);
        if (input.isAreaClicked(x + w - 62F, y + 32F, 48F, 28F)) selectedModule.toggle();

        float actionY = y + 98F;
        action(vg, input, x + 14F, actionY, w - 28F, "KEYBIND", awaitingKeyBind ? "PRESS A KEY" : keyName(selectedModule.key));
        if (input.isAreaClicked(x + 14F, actionY, w - 28F, 31F)) awaitingKeyBind = true;
        if (hasSpecialEditor(selectedModule)) {
            action(vg, input, x + 14F, actionY + 38F, w - 28F, "CONFIGURE", "OPEN");
            if (input.isAreaClicked(x + 14F, actionY + 38F, w - 28F, 31F)) openSpecialEditor();
        }

        float settingsY = hasSpecialEditor(selectedModule) ? actionY + 87F : actionY + 49F;
        text(vg, "SETTINGS", x + 18F, settingsY, 11F, MUTED);
        List<ModuleSetting> settings = selectedModule.getSettings();
        int visible = Math.max(1, (int) ((y + h - settingsY - 22F) / 37F));
        int max = Math.max(0, settings.size() - visible);
        settingsScroll = Math.max(0, Math.min(settingsScroll, max));
        for (int i = settingsScroll; i < Math.min(settings.size(), settingsScroll + visible); i++) {
            float sy = settingsY + 16F + (i - settingsScroll) * 37F;
            ModuleSetting setting = settings.get(i);
            nvg.drawRoundedRect(vg, x + 14F, sy, w - 28F, 31F, CARD, 9F);
            text(vg, trim(vg, setting.name, w - 112F, 11F), x + 24F, sy + 10F, 11F, TEXT);
            text(vg, "−", x + w - 80F, sy + 7F, 16F, YELLOW);
            textRight(vg, Integer.toString(setting.get()), x + w - 42F, sy + 10F, 11F, TEXT);
            text(vg, "+", x + w - 30F, sy + 7F, 16F, YELLOW);
            if (input.isAreaClicked(x + w - 89F, sy, 25F, 31F)) setting.adjust(-1);
            else if (input.isAreaClicked(x + w - 34F, sy, 25F, 31F)) setting.adjust(1);
        }
        popClip();
    }

    private void action(long vg, InputHandler input, float x, float y, float w, String label, String value) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        boolean hover = input.isAreaHovered(x, y, w, 31F);
        nvg.drawRoundedRect(vg, x, y, w, 31F, hover ? CARD_HOVER : CARD, 9F);
        float valueY = lineY(y, 31F, 10F);
        text(vg, label, x + 12F, valueY, 10F, MUTED);
        textRight(vg, value, x + w - 16F, valueY, 10F, YELLOW);
    }

    private void pill(long vg, InputHandler input, float x, float y, float w, float h, String label, String icon, boolean active) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        boolean hover = input.isAreaHovered(x, y, w, h);
        nvg.drawRoundedRect(vg, x, y, w, h, active ? YELLOW_DARK : hover ? CARD_HOVER : SURFACE, h * .5F);
        svg(vg, icon, x + 10F, y + 10F, 14F, 0xFFFFFFFF);
        text(vg, label, x + 29F, lineY(y, h, 11F), 11F, active ? YELLOW : TEXT);
    }

    private void iconButton(long vg, InputHandler input, float x, float y, float size, String icon) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        boolean hover = input.isAreaHovered(x, y, size, size);
        nvg.drawRoundedRect(vg, x, y, size, size, hover ? CARD_HOVER : SURFACE, size * .5F);
        svg(vg, icon, x + 10F, y + 10F, 14F, 0xFFFFFFFF);
    }

    private void drawToggle(long vg, InputHandler input, float x, float y, boolean enabled) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        nvg.drawRoundedRect(vg, x, y, 34F, 18F, enabled ? YELLOW : 0xFF3A424B, 9F);
        nvg.drawCircle(vg, x + (enabled ? 25F : 9F), y + 9F, 6F, enabled ? 0xFF171208 : 0xFFB7BBC0);
    }

    private List<Module> visibleModules() {
        List<Module> result = new ArrayList<Module>();
        String query = search.trim().toLowerCase(Locale.ROOT);
        for (Module module : GoldForge.MODULES.all()) {
            if (query.isEmpty() && module.category != selectedCategory) continue;
            if (!query.isEmpty() && !module.name.toLowerCase(Locale.ROOT).contains(query)
                    && !module.description.toLowerCase(Locale.ROOT).contains(query)
                    && !module.category.name().toLowerCase(Locale.ROOT).contains(query)) continue;
            result.add(module);
        }
        return result;
    }

    @Override public void onMouseScrolled(double amount) {
        if (amount == 0D) return;
        moduleScroll = Math.max(0, moduleScroll + (amount > 0D ? -2 : 2));
    }

    @Override public void onKeyPressed(int keyCode, char typedChar, UKeyboard.Modifiers modifiers) {
        if (awaitingKeyBind && selectedModule != null) {
            selectedModule.setKey(keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_BACK ? 0 : keyCode);
            awaitingKeyBind = false;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_F && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            searchFocused = true;
            return;
        }
        if (!searchFocused && typedChar == '/') {
            searchFocused = true;
            return;
        }
        if (!searchFocused) return;
        if (keyCode == Keyboard.KEY_RETURN) {
            searchFocused = false;
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
            moduleScroll = 0;
            return;
        }
        if (Character.isLetterOrDigit(typedChar) || Character.isWhitespace(typedChar) || "_-".indexOf(typedChar) >= 0) {
            search += typedChar;
            moduleScroll = 0;
        }
    }

    @Override public void onScreenClose() { ConfigManager.save(); }

    private boolean hasSpecialEditor(Module module) {
        return module instanceof CustomChat || module instanceof ShaderFX || module instanceof KillFX
                || module instanceof BlockESP || module instanceof AutoSign;
    }

    private void openSpecialEditor() {
        if (selectedModule instanceof CustomChat) ((CustomChat) selectedModule).chooseFont();
        else if (selectedModule instanceof ShaderFX) ((ShaderFX) selectedModule).chooseTexture();
        else if (selectedModule instanceof KillFX) ((KillFX) selectedModule).chooseSound();
        else if (selectedModule instanceof BlockESP) mc.displayGuiScreen(new BlockSelectGui(this, (BlockESP) selectedModule));
        else if (selectedModule instanceof AutoSign) mc.displayGuiScreen(new SignTextGui(this, (AutoSign) selectedModule));
    }

    private String trim(long vg, String value, float maxWidth, float size) {
        if (NanoVGHelper.INSTANCE.getTextWidth(vg, value, size, Fonts.REGULAR) <= maxWidth) return value;
        String suffix = "…";
        int length = value.length();
        while (length > 1 && NanoVGHelper.INSTANCE.getTextWidth(vg, value.substring(0, length) + suffix, size, Fonts.REGULAR) > maxWidth) length--;
        return value.substring(0, Math.max(1, length)) + suffix;
    }

    /** Prefer a complete final word before the ellipsis so card summaries read cleanly. */
    private String trimWords(long vg, String value, float maxWidth, float size) {
        if (NanoVGHelper.INSTANCE.getTextWidth(vg, value, size, Fonts.REGULAR) <= maxWidth) return value;
        String clipped = trim(vg, value, maxWidth, size);
        int suffix = clipped.length() - 1;
        int breakAt = clipped.lastIndexOf(' ', suffix - 1);
        return breakAt > 2 ? clipped.substring(0, breakAt) + "…" : clipped;
    }

    /** Draw one compact line on short cards and two whole-word lines when room permits. */
    private void drawDescription(long vg, String value, float x, float y, float maxWidth, float cardHeight, int color) {
        final float size = 10F;
        if (cardHeight < 58F || NanoVGHelper.INSTANCE.getTextWidth(vg, value, size, Fonts.REGULAR) <= maxWidth) {
            text(vg, trimWords(vg, value, maxWidth, size), x, lineY(y, 12F, size), size, color);
            return;
        }
        int cut = value.length();
        while (cut > 1 && NanoVGHelper.INSTANCE.getTextWidth(vg, value.substring(0, cut), size, Fonts.REGULAR) > maxWidth) cut--;
        int word = value.lastIndexOf(' ', cut - 1);
        if (word <= 2) {
            text(vg, trimWords(vg, value, maxWidth, size), x, lineY(y, 12F, size), size, color);
            return;
        }
        String first = value.substring(0, word).trim();
        String second = value.substring(word + 1).trim();
        text(vg, first, x, lineY(y, 12F, size), size, color);
        text(vg, trimWords(vg, second, maxWidth, size), x, lineY(y + 10F, 12F, size), size, color);
    }

    private float fitTextSize(long vg, String value, float maxWidth, float preferred, float minimum) {
        float size = preferred;
        while (size > minimum && NanoVGHelper.INSTANCE.getTextWidth(vg, value, size, Fonts.REGULAR) > maxWidth) size -= .5F;
        return size;
    }

    private String keyName(int key) { return key == 0 ? "UNBOUND" : Keyboard.getKeyName(key); }
    private float lineY(float y, float height, float textSize) { return y + (height - textSize) * .5F; }
    /** NanoVG owns geometry, while this physical framebuffer scissor keeps each panel's children inside its rounded bounds. */
    private void pushClip(float x, float y, float w, float h) {
        float scaleX = mc.displayWidth / Math.max(1F, width);
        float scaleY = mc.displayHeight / Math.max(1F, height);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(Math.round(x * scaleX), Math.round((height - y - h) * scaleY),
                Math.max(1, Math.round(w * scaleX)), Math.max(1, Math.round(h * scaleY)));
    }
    private void popClip() { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
    private String iconFor(Category category) {
        switch (category) {
            case COMBAT: return "crosshair";
            case MOVEMENT: return "person-standing";
            case PLAYER: return "user-round";
            case RENDER: return "monitor";
            case WORLD: return "box";
            case EXPLOIT: return "zap";
            case MISC: return "sparkles";
            case CLIENT: return "sliders-horizontal";
            case HUD: return "layout-dashboard";
            default: return "circle";
        }
    }
    private void svg(long vg, String icon, float x, float y, float size, int color) {
        NanoVGHelper.INSTANCE.drawSvg(vg, "/assets/goldforge/icons/" + icon + ".svg", x, y, size, size, color, 1F, ModernClickGui.class);
    }
    /**
     * OneConfig's regular text is vertically middle-aligned. All layout code
     * in this class supplies a visual top edge, so adapt once here instead of
     * making every card, row and header compensate independently.
     */
    private void text(long vg, String value, float x, float y, float size, int color) {
        NanoVGHelper.INSTANCE.drawText(vg, value, x, y + size * .5F, color, size, Fonts.REGULAR);
    }
    private void textCentered(long vg, String value, float x, float y, float size, int color) { NanoVGHelper.INSTANCE.drawCenteredText(vg, value, x, y, color, size, Fonts.REGULAR); }
    private void textRight(long vg, String value, float right, float y, float size, int color) { text(vg, value, right - NanoVGHelper.INSTANCE.getTextWidth(vg, value, size, Fonts.REGULAR), y, size, color); }
}
