package dev.goldforge.gui;

import dev.goldforge.GoldForge;
import dev.goldforge.ConfigManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.module.ModuleSetting;
import dev.goldforge.module.impl.BlockESP;
import dev.goldforge.module.impl.DIESP;
import dev.goldforge.module.impl.AutoSign;
import dev.goldforge.module.impl.CustomChat;
import dev.goldforge.module.impl.KillFX;
import dev.goldforge.module.impl.ShaderFX;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Three-pane, compact module browser with a persistent settings inspector. */
public final class ClickGui extends GuiScreen {
    private final GuiScreen parent;
    private int PANEL = 0xFF0B1117;
    private int CARD = 0xFF131B22;
    private int CARD_HOVER = 0xFF1A2830;
    private int LINE = 0xFF26343C;
    private int TEXT = 0xFFF0F5F5;
    private int MUTED = 0xFF85959C;
    private int MINT = 0xFFFFC928;
    private int GOLD = 0xFFFFD34D;

    private static final float DESIGN_W = 680F;
    private static final float DESIGN_H = 380F;
    private static final float SIDEBAR_X = 14F;
    private static final float SIDEBAR_W = 112F;
    private static final float LIST_X = 142F;
    private static final float LIST_W = 350F;
    private static final float DETAIL_X = 506F;
    private static final float DETAIL_W = 160F;

    private Category selectedCategory;
    private Module selectedModule;
    private UiFont uiFont;
    private String search = "";
    private boolean searchFocused;
    private boolean awaitingKeyBind;
    private int moduleScroll;
    private int settingsScroll;
    private long openedAt;
    private float panelScale = 1F;
    private float panelLeft;
    private float panelTop;
    private int draggingPanel=-1,dragOffsetX,dragOffsetY;
    private final int[] panelScroll=new int[Category.values().length];
    private PanelRect[] floatingRects=new PanelRect[0];

    public ClickGui() { this(null); }
    public ClickGui(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        // The legacy floating panels were kept for old configs during the initial
        // transition.  The library is now one cohesive surface, so always open the
        // card view instead of restoring an obsolete layout from disk.
        GuiPreferences.layout = 0;
        openedAt = System.currentTimeMillis();
        panelScale = Math.min(1F, Math.min((width - 16F) / DESIGN_W, (height - 16F) / DESIGN_H));
        panelLeft = (width - DESIGN_W * panelScale) * .5F;
        panelTop = (height - DESIGN_H * panelScale) * .5F;
        try { uiFont = new UiFont(null, 10.5F); }
        catch (Exception ignored) { uiFont = null; }
    }

    @Override public void onGuiClosed() {
        if (uiFont != null) { uiFont.dispose(); uiFont = null; }
        UiSurface.dispose();
    }

    private void text(String value, float x, float y, int color) {
        if (uiFont != null) uiFont.draw(value, x, y, color);
        else fontRenderer.drawString(value, (int) x, (int) y, color);
    }

    private int textWidth(String value) {
        return uiFont != null ? uiFont.width(value) : fontRenderer.getStringWidth(value);
    }

    private String clipped(String value, int maxWidth) {
        if (uiFont != null) return uiFont.trimToWidth(value, maxWidth);
        return fontRenderer.trimStringToWidth(value, maxWidth);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateTheme();
        float mx = (mouseX - panelLeft) / panelScale;
        float my = (mouseY - panelTop) / panelScale;
        float fade = Math.min(1F, (System.currentTimeMillis() - openedAt) / 160F);
        int previousProgram = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean stencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            // GuiScreen is an orthographic overlay. Stale world depth can cut holes
            // through cards along terrain edges, and a model GLSL program cannot
            // shade POSITION_COLOR / POSITION_TEX GUI batches correctly.
            org.lwjgl.opengl.GL20.glUseProgram(0);
            // Use raw calls here: other 1.12 mods can desynchronize GlStateManager's cache.
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDepthMask(false);
            // Keep the canvas in the vanilla GUI pipeline. Several 1.12 shader packs
            // leave a program bound that makes custom GUI shaders transparent.
            Gui.drawRect(0, 0, width, height, 0xFF050508);

            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(panelLeft, panelTop, 0F);
                GlStateManager.scale(panelScale, panelScale, 1F);
                drawPanel(fade, mx, my);
            } finally {
                GlStateManager.popMatrix();
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            GL11.glDepthMask(depthWrite);
            if (depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (stencilEnabled) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL20.glUseProgram(previousProgram);
        }
    }

    private void drawPanel(float fade, float mx, float my) {
        // Layered glass shell: diffuse gold outside, near-black within.
        roundedRect(-4, -4, DESIGN_W + 8, DESIGN_H + 8, 18, 0x243F3100);
        roundedRect(-2, -2, DESIGN_W + 4, DESIGN_H + 4, 16, 0x4A826500);
        roundedRect(0, 0, DESIGN_W, DESIGN_H, 15, 0xFF584914);
        roundedRect(2, 2, DESIGN_W - 4, DESIGN_H - 4, 13, 0xF8080A0D);
        roundedRect(3, 3, DESIGN_W - 6, 2, 1, withAlpha(MINT, fade));

        drawHeader(mx, my);
        drawSidebar(mx, my);
        drawModuleList(mx, my);
        drawDetails(mx, my);
        text("RIGHT SHIFT  /  ESC CLOSE", 17, 362, MUTED);
        text("BACKSPACE  |  " + GoldForge.VERSION, 535, 362, 0xFF596970);
    }

    private void drawHeader(float mx, float my) {
        BrandMark.draw(15, 12, 20);
        text("BACKSPACE", 42, 10, GOLD);
        text(GoldForge.VERSION, 42, 25, MUTED);
        line(15, 46, DESIGN_W - 15, 47, LINE);

        roundedRect(365, 11, 175, 26, 13, searchFocused ? CARD_HOVER : CARD);
        searchIcon(379, 24);
        text(search.isEmpty() ? "Search modules..." : clipped(search, 122), 393, 17,
                search.isEmpty() ? MUTED : TEXT);
        if (!search.isEmpty()) text("X", 520, 17, MUTED);

        drawHeaderButton(552,10,42,28,"HUD",inside(mx,my,552,10,42,28));
        drawHeaderButton(602,10,64,28,themeLabel(),inside(mx,my,602,10,64,28));
    }

    private void drawFloatingPanels(int mouseX,int mouseY,float fade){
        drawRect(0,0,width,height,0x8103070B);
        BrandMark.draw(14,8,18);text("BACKSPACE",39,8,GOLD);text("FLOATING MODULE PANELS",39,21,MUTED);
        text("Drag headers · click to toggle · right-click for settings",Math.max(190,width/2-115),15,MUTED);
        int hudX=width-160,libraryX=width-108,themeX=width-48;
        drawFloatingButton(hudX,8,48,"HUD",mouseX,mouseY);
        drawFloatingButton(libraryX,8,52,"LIBRARY",mouseX,mouseY);
        drawFloatingButton(themeX,8,40,themeLabel(),mouseX,mouseY);

        Category[] categories=Category.values();int columns=Math.min(3,Math.max(1,(width-20)/150));
        int rows=(categories.length+columns-1)/columns,gap=7,top=37;
        int panelWidth=Math.max(128,(width-20-(columns-1)*gap)/columns);
        int panelHeight=Math.min(168,Math.max(84,(height-top-10-(rows-1)*gap)/Math.max(1,rows)));
        floatingRects=new PanelRect[categories.length];
        for(Category category:categories){
            int index=category.ordinal(),column=index%columns,row=index/columns;
            int defaultX=10+column*(panelWidth+gap),defaultY=top+row*(panelHeight+gap);
            int x=GuiPreferences.panelX[index]<0?defaultX:GuiPreferences.panelX[index];
            int y=GuiPreferences.panelY[index]<0?defaultY:GuiPreferences.panelY[index];
            x=Math.max(0,Math.min(Math.max(0,width-panelWidth),x));y=Math.max(29,Math.min(Math.max(29,height-panelHeight),y));
            if(draggingPanel==index){GuiPreferences.panelX[index]=x;GuiPreferences.panelY[index]=y;}
            PanelRect rect=new PanelRect(category,x,y,panelWidth,panelHeight);floatingRects[index]=rect;
            drawFloatingCategory(rect,mouseX,mouseY,fade);
        }
    }

    private void drawFloatingButton(int x,int y,int w,String label,int mouseX,int mouseY){
        boolean hover=inside(mouseX,mouseY,x,y,w,24);roundedRect(x,y,w,24,10,hover?CARD_HOVER:PANEL);
        roundedRect(x+2,y+2,w-4,2,1,MINT);text(label,x+(w-textWidth(label))/2F,y+7,hover?GOLD:TEXT);
    }

    private void drawFloatingCategory(PanelRect rect,int mouseX,int mouseY,float fade){
        int index=rect.category.ordinal();boolean collapsed=GuiPreferences.collapsed[index];
        int h=collapsed?21:rect.height;
        roundedRect(rect.x+2,rect.y+3,rect.width, h,10,0x55000000);
        roundedRect(rect.x,rect.y,rect.width,h,10,withAlpha(PANEL,fade));
        roundedRect(rect.x+2,rect.y+2,rect.width-4,18,8,withAlpha(MINT,fade));
        text(rect.category.name(),rect.x+8,rect.y+5,TEXT);
        List<Module> modules=GoldForge.MODULES.in(rect.category);
        String count=Integer.toString(modules.size());text(count,rect.x+rect.width-33-textWidth(count),rect.y+5,TEXT);
        int controlX=rect.x+rect.width-19;boolean controlHover=inside(mouseX,mouseY,controlX,rect.y+1,17,17);
        int controlColor=controlHover?GOLD:TEXT;
        Gui.drawRect(controlX+4,rect.y+8,controlX+13,rect.y+9,controlColor);
        if(collapsed)Gui.drawRect(controlX+8,rect.y+4,controlX+9,rect.y+13,controlColor);
        if(collapsed)return;
        int visibleRows=Math.max(1,(rect.height-25)/14);
        int scroll=Math.max(0,Math.min(panelScroll[index],Math.max(0,modules.size()-visibleRows)));panelScroll[index]=scroll;
        for(int row=0;row<visibleRows&&scroll+row<modules.size();row++){
            Module module=modules.get(scroll+row);int y=rect.y+23+row*14;
            boolean hover=inside(mouseX,mouseY,rect.x+2,y-1,rect.width-4,14);
            if(module.enabled||hover)roundedRect(rect.x+2,y-1,rect.width-4,13,5,module.enabled?withAlpha(MINT,.30F):CARD_HOVER);
            text(clipped(module.name,rect.width-34),rect.x+8,y+1,module.enabled?GOLD:TEXT);
            circle(rect.x+rect.width-12,y+6,3F,module.enabled?MINT:0xFF65737A);
        }
        if(modules.size()>visibleRows){int trackX=rect.x+rect.width-3;Gui.drawRect(trackX,rect.y+22,trackX+1,rect.y+rect.height-3,LINE);}
    }

    private void handleFloatingClick(int mouseX,int mouseY,int button){
        if(mouseY>=8&&mouseY<=32){
            if(mouseX>=width-160&&mouseX<=width-112){mc.displayGuiScreen(new HudEditor(this));return;}
            if(mouseX>=width-108&&mouseX<=width-56){GuiPreferences.layout=0;ConfigManager.save();return;}
            if(mouseX>=width-48&&mouseX<=width-8){GuiPreferences.theme=(GuiPreferences.theme+1)%4;ConfigManager.save();return;}
        }
        if(floatingRects==null)return;
        for(PanelRect rect:floatingRects){
            int index=rect.category.ordinal();boolean collapsed=GuiPreferences.collapsed[index];
            if(!inside(mouseX,mouseY,rect.x,rect.y,rect.width,collapsed?21:rect.height))continue;
            if(mouseY<=rect.y+19){
                if(mouseX>=rect.x+rect.width-21){GuiPreferences.collapsed[index]=!collapsed;ConfigManager.save();}
                else if(button==0){draggingPanel=index;dragOffsetX=mouseX-rect.x;dragOffsetY=mouseY-rect.y;GuiPreferences.panelX[index]=rect.x;GuiPreferences.panelY[index]=rect.y;}
                return;
            }
            if(collapsed)return;
            int visibleRows=Math.max(1,(rect.height-25)/14),modulesScroll=panelScroll[index];
            int row=(mouseY-(rect.y+23))/14;if(row<0||row>=visibleRows)return;
            List<Module> modules=GoldForge.MODULES.in(rect.category);int selected=modulesScroll+row;
            if(selected>=modules.size())return;Module module=modules.get(selected);
            if(button==1){selectedModule=module;settingsScroll=0;GuiPreferences.layout=0;ConfigManager.save();}
            else if(button==0)module.toggle();
            return;
        }
    }

    private static final class PanelRect{
        final Category category;final int x,y,width,height;
        PanelRect(Category category,int x,int y,int width,int height){this.category=category;this.x=x;this.y=y;this.width=width;this.height=height;}
    }

    private void drawHeaderButton(int x,int y,int w,int h,String label,boolean hover){
        roundedRect(x,y,w,h,14,hover?CARD_HOVER:0xFF101214);
        text(label,x+(w-textWidth(label))/2F,y+10,hover?GOLD:MINT);
    }

    private String themeLabel(){switch(GuiPreferences.theme){case 1:return "RED";case 2:return "MINT";case 3:return "BLUE";default:return "GOLD";}}

    private void updateTheme(){
        switch(GuiPreferences.theme){
            case 1:PANEL=0xFF100B0D;CARD=0xFF211518;CARD_HOVER=0xFF321D21;LINE=0xFF48282D;TEXT=0xFFF7F0F0;MUTED=0xFFB19799;MINT=0xFFE0444C;GOLD=0xFFFF7777;break;
            case 2:PANEL=0xFF0A1214;CARD=0xFF121F20;CARD_HOVER=0xFF1B302F;LINE=0xFF29413F;TEXT=0xFFEAF5F3;MUTED=0xFF91AAA5;MINT=0xFF62E3BB;GOLD=0xFF9AF2D5;break;
            case 3:PANEL=0xFF0A101A;CARD=0xFF121D2A;CARD_HOVER=0xFF1B2B3D;LINE=0xFF293C52;TEXT=0xFFEAF1FA;MUTED=0xFF91A4BA;MINT=0xFF65B8FF;GOLD=0xFFA5D8FF;break;
            default:PANEL=0xFF0B1117;CARD=0xFF131B22;CARD_HOVER=0xFF1A2830;LINE=0xFF26343C;TEXT=0xFFF0F5F5;MUTED=0xFF85959C;MINT=0xFFFFC928;GOLD=0xFFFFD34D;break;
        }
    }

    private void drawSidebar(float mx, float my) {
        text("LIBRARY", SIDEBAR_X + 2, 59, MUTED);
        Category[] categories = Category.values();
        for (int i = 0; i <= categories.length; i++) {
            Category category = i == 0 ? null : categories[i - 1];
            int y = 76 + i * 25;
            boolean active = selectedCategory == category;
            boolean hover = inside(mx, my, SIDEBAR_X, y, SIDEBAR_W, 21);
            if (active) {
                roundedRect(SIDEBAR_X, y, SIDEBAR_W, 21, 10, 0xFF47380D);
                roundedRect(SIDEBAR_X + 3, y + 3, 2, 15, 1, MINT);
            } else if (hover) roundedRect(SIDEBAR_X, y, SIDEBAR_W, 21, 10, 0xFF17232A);
            String label = category == null ? "ALL" : category.name();
            circle(SIDEBAR_X + 16, y + 10.5F, 3.2F, active ? MINT : (hover ? TEXT : 0xFF536066));
            text(label, SIDEBAR_X + 26, y + 5, active ? MINT : (hover ? TEXT : MUTED));
            int count = countFor(category);
            String countLabel = Integer.toString(count);
            text(countLabel, SIDEBAR_X + SIDEBAR_W - textWidth(countLabel) - 8, y + 5,
                    active ? 0xFFFFD972 : 0xFF52636B);
        }
    }

    private void drawModuleList(float mx, float my) {
        line(SIDEBAR_X + SIDEBAR_W + 7, 57, SIDEBAR_X + SIDEBAR_W + 8, 348, LINE);
        String heading = selectedCategory == null ? "ALL MODULES" : selectedCategory.name();
        text(heading, LIST_X + 2, 59, TEXT);
        text(Integer.toString(visibleModules().size()), LIST_X + LIST_W - 14, 59, MUTED);

        List<Module> visible = visibleModules();
        int rowTop = 78, rowStep = 54, rowHeight = 48, cardWidth = 170, visibleCards = 10;
        moduleScroll = Math.max(0, Math.min(moduleScroll, Math.max(0, visible.size() - visibleCards)));
        if (selectedModule != null && !visible.contains(selectedModule)) selectedModule = null;
        for (int card = 0; card < visibleCards && moduleScroll + card < visible.size(); card++) {
            Module module = visible.get(moduleScroll + card);
            int column = card & 1, row = card >> 1;
            float x = LIST_X + column * (cardWidth + 10), y = rowTop + row * rowStep;
            boolean active = module == selectedModule, hover = inside(mx, my, x, y, cardWidth, rowHeight);
            roundedRect(x, y, cardWidth, rowHeight, 13,
                    active ? 0xFF30270F : hover ? CARD_HOVER : CARD);
            circle(x + 17, y + 24, 10F, module.enabled ? 0xFF4B3B0B : 0xFF273039);
            text(clipped(module.name, 104), x + 34, y + 10, module.enabled ? MINT : TEXT);
            text(clipped(module.description, 112), x + 34, y + 25, MUTED);
            drawToggle(x + cardWidth - 35, y + 16, module.enabled, hover);
        }
        if (visible.isEmpty()) text("No matching modules", LIST_X + 7, rowTop + 8, MUTED);
    }

    private void drawToggle(float x, float y, boolean enabled, boolean hover) {
        roundedRect(x, y, 24, 14, 7, enabled ? MINT : (hover ? 0xFF495159 : 0xFF303940));
        circle(x + (enabled ? 17F : 7F), y + 7F, 5F, enabled ? 0xFFFFFFFF : 0xFFB6BDC2);
    }

    private void drawDetails(float mx, float my) {
        line(DETAIL_X - 8, 57, DETAIL_X - 7, 348, LINE);
        roundedRect(DETAIL_X, 57, DETAIL_W, 291, 16, 0xFF111214);
        if (selectedModule == null) {
            text("MODULE DETAILS", DETAIL_X + 14, 70, MUTED);
            text("Choose a module", DETAIL_X + 14, 105, TEXT);
            text("Select an entry in the", DETAIL_X + 14, 129, MUTED);
            text("card grid to view settings.", DETAIL_X + 14, 143, MUTED);
            text("TIP", DETAIL_X + 14, 191, MINT);
            text("Toggle directly, or select", DETAIL_X + 14, 210, MUTED);
            text("a card for settings.", DETAIL_X + 14, 224, MUTED);
            return;
        }

        Module module = selectedModule;
        text(module.category.name(), DETAIL_X + 14, 68, MINT);
        text(clipped(module.name, 128), DETAIL_X + 14, 84, TEXT);
        List<String> description = wrap(module.description, 128);
        for (int i = 0; i < Math.min(2, description.size()); i++)
            text(description.get(i), DETAIL_X + 14, 103 + i * 12, MUTED);

        roundedRect(DETAIL_X + 12, 132, DETAIL_W - 24, 25, 10, module.enabled ? 0xFF47380D : 0xFF202A30);
        text(module.enabled ? "ENABLED · TOGGLE" : "DISABLED · TOGGLE", DETAIL_X + 20, 138, module.enabled ? MINT : MUTED);
        roundedRect(DETAIL_X + 12, 162, DETAIL_W - 24, 21, 9,
                awaitingKeyBind ? 0xFF3A3420 : (inside(mx, my, DETAIL_X + 12, 162, DETAIL_W - 24, 21) ? CARD_HOVER : CARD));
        String key = awaitingKeyBind ? "PRESS KEY" : (module.key == 0 ? "BIND KEY" : Keyboard.getKeyName(module.key));
        text(clipped(key, 120), DETAIL_X + 20, 167, awaitingKeyBind ? GOLD : TEXT);

        drawAssetControl(module, mx, my);
        text(module instanceof ShaderFX ? clipped(((ShaderFX) module).shaderStatus(), 128) : "SETTINGS", DETAIL_X + 14, 214, MUTED);
        drawSettings(module, mx, my);
    }

    private void drawAssetControl(Module module, float mx, float my) {
        String label;
        String value;
        if (module instanceof CustomChat) {
            label = "IMPORT FONT"; value = ((CustomChat) module).fontLabel();
        } else if (module instanceof ShaderFX) {
            label = "OVERLAY IMAGE"; value = ((ShaderFX) module).textureLabel();
        } else if (module instanceof KillFX) {
            label = "IMPORT WAV"; value = ((KillFX) module).soundLabel();
        } else if (module instanceof BlockESP) {
            label = "BLOCK TARGETS"; value = ((BlockESP) module).selectedBlocks().size() + " selected";
        } else if (module instanceof DIESP) {
            label = "ITEM TARGETS"; value = ((DIESP) module).selectedCount() + " selected";
        } else if (module instanceof AutoSign) {
            label = "EDIT SIGN TEXT"; value = "4 lines / formatting";
        } else {
            label = "MODULE"; value = "READY";
        }
        boolean hover = inside(mx, my, DETAIL_X + 12, 188, DETAIL_W - 24, 19);
        roundedRect(DETAIL_X + 12, 188, DETAIL_W - 24, 19, 8, hover ? CARD_HOVER : CARD);
        text(clipped(label, 74), DETAIL_X + 20, 192, MINT);
        text(clipped(value, 48), DETAIL_X + DETAIL_W - 60, 192, TEXT);
    }

    private void drawSettings(Module module, float mx, float my) {
        if (module instanceof BlockESP) {
            drawBlockSettings((BlockESP) module, mx, my);
            return;
        }
        List<ModuleSetting> settings = module.getSettings();
        int rowY = 224;
        int rowStep = 24;
        int visibleRows = 5;
        settingsScroll = Math.max(0, Math.min(settingsScroll, Math.max(0, settings.size() - visibleRows)));
        for (int index = settingsScroll; index < Math.min(settings.size(), settingsScroll + visibleRows); index++) {
            ModuleSetting setting = settings.get(index);
            int y = rowY + (index - settingsScroll) * rowStep;
            boolean hover = inside(mx, my, DETAIL_X + 12, y, DETAIL_W - 24, 21);
            roundedRect(DETAIL_X + 12, y, DETAIL_W - 24, 21, 5, hover ? CARD_HOVER : CARD);
            text(clipped(setting.name, 73), DETAIL_X + 19, y + 5, TEXT);
            text("-", DETAIL_X + 93, y + 4, MINT);
            String value = Integer.toString(setting.get());
            text(clipped(value, 22), DETAIL_X + 108, y + 5, 0xFFD6E1E3);
            text("+", DETAIL_X + 131, y + 4, MINT);
        }
        drawScrollbar(DETAIL_X + DETAIL_W - 7, rowY, visibleRows * rowStep - 3, settings.size(), settingsScroll, visibleRows);
    }

    private void drawBlockSettings(BlockESP module, float mx, float my) {
        int rowY = 224;
        boolean hover = inside(mx, my, DETAIL_X + 12, rowY, DETAIL_W - 24, 21);
        roundedRect(DETAIL_X + 12, rowY, DETAIL_W - 24, 21, 5, hover ? CARD_HOVER : CARD);
        text("OPEN BLOCK CATALOG", DETAIL_X + 21, rowY + 5, MINT);
        List<Block> selected = module.selectedBlocks();
        text(selected.size() + " selected", DETAIL_X + 19, 252, MUTED);
        int iconX = (int) DETAIL_X + 20;
        for (int i = 0; i < Math.min(8, selected.size()); i++) {
            Item item = Item.getItemFromBlock(selected.get(i));
            if (item != null && item != net.minecraft.init.Items.AIR) {
                GlStateManager.color(1F, 1F, 1F, 1F);
                mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(item), iconX, 272);
                iconX += 22;
            }
        }
    }

    private void drawScrollbar(float x, float y, float height, int count, int offset, int visible) {
        if (count <= visible) return;
        Gui.drawRect((int) x, (int) y, (int) x + 2, (int) (y + height), 0xFF26343C);
        float thumbHeight = Math.max(16F, height * visible / count);
        float thumbY = y + (height - thumbHeight) * offset / (count - visible);
        roundedRect(x - 1, thumbY, 4, thumbHeight, 2, MINT);
    }

    private List<Module> visibleModules() {
        List<Module> result = new ArrayList<Module>();
        String query = search.trim().toLowerCase(Locale.ROOT);
        List<Module> candidates = selectedCategory == null || !query.isEmpty()
                ? GoldForge.MODULES.all() : GoldForge.MODULES.in(selectedCategory);
        for (Module module : candidates) {
            if (selectedCategory != null && query.isEmpty() && module.category != selectedCategory) continue;
            if (query.isEmpty() || module.name.toLowerCase(Locale.ROOT).contains(query)
                    || module.description.toLowerCase(Locale.ROOT).contains(query)
                    || module.category.name().toLowerCase(Locale.ROOT).contains(query)) result.add(module);
        }
        return result;
    }

    private int countFor(Category category) {
        if (category == null) return GoldForge.MODULES.all().size();
        return GoldForge.MODULES.in(category).size();
    }

    private List<String> wrap(String value, int maxWidth) {
        List<String> lines = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && textWidth(candidate) > maxWidth) {
                lines.add(line.toString()); line.setLength(0); line.append(word);
            } else {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int withAlpha(int color, float opacity) {
        return (color & 0x00FFFFFF) | ((int) (((color >>> 24) & 255) * opacity) << 24);
    }

    private void line(float x1, float y1, float x2, float y2, int color) {
        Gui.drawRect((int) x1, (int) y1, (int) x2, (int) y2, color);
    }

    private void circle(float x, float y, float radius, int color) {
        float a = ((color >>> 24) & 255) / 255F, r = ((color >>> 16) & 255) / 255F;
        float g = ((color >>> 8) & 255) / 255F, b = (color & 255) / 255F;
        GlStateManager.enableBlend(); GlStateManager.disableTexture2D();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(x, y, 0).color(r, g, b, a).endVertex();
        for (int i = 0; i <= 24; i++) {
            double angle = Math.PI * 2D * i / 24D;
            buffer.pos(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius, 0).color(r, g, b, a).endVertex();
        }
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D(); GlStateManager.disableBlend(); GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private void searchIcon(float x, float y) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(.55F, .69F, .70F, 1F); GL11.glLineWidth(1.3F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 20; i++) {
            double angle = Math.PI * 2D * i / 20D;
            GL11.glVertex2d(x + Math.cos(angle) * 4D, y + Math.sin(angle) * 4D);
        }
        GL11.glEnd(); GL11.glBegin(GL11.GL_LINES); GL11.glVertex2d(x + 3D, y + 3D); GL11.glVertex2d(x + 7D, y + 7D); GL11.glEnd();
        GL11.glPopAttrib();
    }

    private void roundedRect(float x, float y, float w, float h, float radius, int argb) {
        if (!UiSurface.draw(x, y, w, h, radius, argb) && w > 0F && h > 0F)
            Gui.drawRect(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), argb);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        float mx = (mouseX - panelLeft) / panelScale;
        float my = (mouseY - panelTop) / panelScale;
        if (inside(mx, my, 552, 10, 42, 28)) {
            mc.displayGuiScreen(new HudEditor(this)); return;
        }
        if(inside(mx,my,602,10,64,28)){GuiPreferences.theme=(GuiPreferences.theme+1)%4;ConfigManager.save();return;}
        if (inside(mx, my, 365, 11, 175, 26)) {
            if (inside(mx, my, 512, 11, 28, 26)) search = "";
            searchFocused = true; moduleScroll = 0; return;
        }

        Category[] categories = Category.values();
        for (int i = 0; i <= categories.length; i++) {
            int y = 76 + i * 25;
            if (inside(mx, my, SIDEBAR_X, y, SIDEBAR_W, 21)) {
                selectedCategory = i == 0 ? null : categories[i - 1];
                moduleScroll = 0; selectedModule = null; settingsScroll = 0; searchFocused = false; return;
            }
        }

        List<Module> visible = visibleModules();
        int rowTop = 78, rowStep = 54, rowHeight = 48, cardWidth = 170, visibleCards = 10;
        for (int card = 0; card < visibleCards && moduleScroll + card < visible.size(); card++) {
            int column = card & 1, row = card >> 1;
            float x = LIST_X + column * (cardWidth + 10), y = rowTop + row * rowStep;
            if (!inside(mx, my, x, y, cardWidth, rowHeight)) continue;
            Module module = visible.get(moduleScroll + card);
            if (inside(mx, my, x + cardWidth - 39, y + 10, 32, 28)) module.toggle();
            else {
                selectedModule = module;
                settingsScroll = 0;
                awaitingKeyBind = false;
            }
            searchFocused = false;
            return;
        }

        if (selectedModule != null) {
            if (inside(mx, my, DETAIL_X + 12, 132, DETAIL_W - 24, 25)) { selectedModule.toggle(); return; }
            if (inside(mx, my, DETAIL_X + 12, 162, DETAIL_W - 24, 21)) { awaitingKeyBind = true; return; }
            if (inside(mx, my, DETAIL_X + 12, 188, DETAIL_W - 24, 19)) {
                if (selectedModule instanceof CustomChat) ((CustomChat) selectedModule).chooseFont();
                else if (selectedModule instanceof ShaderFX) ((ShaderFX) selectedModule).chooseTexture();
                else if (selectedModule instanceof KillFX) ((KillFX) selectedModule).chooseSound();
                else if (selectedModule instanceof BlockESP) mc.displayGuiScreen(new BlockSelectGui(this, (BlockESP) selectedModule));
                else if (selectedModule instanceof DIESP) mc.displayGuiScreen(new ItemSelectGui(this, (DIESP) selectedModule));
                else if (selectedModule instanceof AutoSign) mc.displayGuiScreen(new SignTextGui(this, (AutoSign) selectedModule));
                return;
            }
            if (selectedModule instanceof BlockESP && inside(mx, my, DETAIL_X + 12, 224, DETAIL_W - 24, 21)) {
                mc.displayGuiScreen(new BlockSelectGui(this, (BlockESP) selectedModule)); return;
            }
            if (!(selectedModule instanceof BlockESP)) {
                int rowY = 224, rowStepSettings = 24;
                List<ModuleSetting> settings = selectedModule.getSettings();
                for (int index = settingsScroll; index < Math.min(settings.size(), settingsScroll + 5); index++) {
                    int y = rowY + (index - settingsScroll) * rowStepSettings;
                    if (!inside(mx, my, DETAIL_X + 12, y, DETAIL_W - 24, 21)) continue;
                    if (inside(mx, my, DETAIL_X + 86, y, 20, 21)) settings.get(index).adjust(-1);
                    else if (inside(mx, my, DETAIL_X + 124, y, 20, 21)) settings.get(index).adjust(1);
                    return;
                }
            }
        }
        searchFocused = false;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (awaitingKeyBind && selectedModule != null) {
            selectedModule.setKey((keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_BACK) ? 0 : keyCode);
            awaitingKeyBind = false; return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        if (keyCode == Keyboard.KEY_F && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            searchFocused = true; return;
        }
        if (!searchFocused && typedChar == '/') { searchFocused = true; return; }
        if (searchFocused) {
            if (keyCode == Keyboard.KEY_RETURN) { searchFocused = false; return; }
            if (keyCode == Keyboard.KEY_BACK) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                moduleScroll = 0; selectedModule = null; return;
            }
            if (Character.isLetterOrDigit(typedChar) || Character.isWhitespace(typedChar) || "_-".indexOf(typedChar) >= 0) {
                search += typedChar; moduleScroll = 0; selectedModule = null; return;
            }
        }
        if (selectedModule == null) {
            List<Module> visible = visibleModules();
            if (!visible.isEmpty() && keyCode == Keyboard.KEY_DOWN) {
                int index = visible.indexOf(selectedModule);
                selectedModule = visible.get(Math.min(visible.size() - 1, Math.max(0, index + 1))); return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getY() * height / mc.displayHeight - 1;
        float mx = (mouseX - panelLeft) / panelScale;
        float my = (mouseY - panelTop) / panelScale;
        if (inside(mx, my, LIST_X, 74, LIST_W, 280)) {
            int max = Math.max(0, visibleModules().size() - 10);
            moduleScroll = Math.max(0, Math.min(max, moduleScroll + (wheel > 0 ? -2 : 2)));
        } else if (selectedModule != null && inside(mx, my, DETAIL_X, 218, DETAIL_W, 126)
                && !(selectedModule instanceof BlockESP)) {
            int max = Math.max(0, selectedModule.getSettings().size() - 5);
            settingsScroll = Math.max(0, Math.min(max, settingsScroll + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override protected void mouseClickMove(int mouseX,int mouseY,int clickedMouseButton,long timeSinceLastClick){
        if(GuiPreferences.layout==1&&draggingPanel>=0&&floatingRects!=null&&draggingPanel<floatingRects.length){
            PanelRect rect=floatingRects[draggingPanel];
            GuiPreferences.panelX[draggingPanel]=Math.max(0,Math.min(Math.max(0,width-rect.width),mouseX-dragOffsetX));
            GuiPreferences.panelY[draggingPanel]=Math.max(29,Math.min(Math.max(29,height-rect.height),mouseY-dragOffsetY));
            return;
        }
        super.mouseClickMove(mouseX,mouseY,clickedMouseButton,timeSinceLastClick);
    }

    @Override protected void mouseReleased(int mouseX,int mouseY,int state){
        if(draggingPanel>=0){draggingPanel=-1;ConfigManager.save();return;}
        super.mouseReleased(mouseX,mouseY,state);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void drawDefaultBackground() {}
}
