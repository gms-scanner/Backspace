package dev.goldforge.module.impl;

import dev.goldforge.GoldForge;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Length-sorted enabled-module list, top longest name to bottom shortest. */
public final class HudArrayList extends Module {
    public int posX=8,posY=34;
    public boolean rightAligned,bottomAnchored;

    public HudArrayList() { super("ArrayList HUD", "Shows enabled modules in a length-sorted HUD list.", Category.HUD, 0); }

    public List<String> labels() {
        List<Module> active=new ArrayList<Module>();
        // Include the list module itself so enabling it always gives immediate visible feedback.
        for(Module module:GoldForge.MODULES.all()) if(module.enabled) active.add(module);
        Collections.sort(active,new Comparator<Module>() {
            @Override public int compare(Module a,Module b) {
                // Sort by the exact label width drawn below, including dynamic HUD tags
                // and the Minecraft font's glyph widths (not Java character count).
                int aWidth=mc.fontRenderer.getStringWidth(a.name+a.hudTag());
                int bWidth=mc.fontRenderer.getStringWidth(b.name+b.hudTag());
                int byWidth=Integer.compare(bWidth,aWidth);
                return byWidth!=0?byWidth:a.name.compareToIgnoreCase(b.name);
            }
        });
        List<String> result=new ArrayList<String>();
        for(Module module:active) result.add(module.name+module.hudTag());
        return result;
    }

    @Override public void onOverlay(RenderGameOverlayEvent.Post event) {
        if(event.getType()!=RenderGameOverlayEvent.ElementType.TEXT || mc.currentScreen instanceof dev.goldforge.gui.HudEditor)return;
        List<String> rows=labels();
        int lineHeight=mc.fontRenderer.FONT_HEIGHT+3;
        int listHeight=rows.size()*lineHeight;
        int top=bottomAnchored?event.getResolution().getScaledHeight()-posY-listHeight:posY;
        int rightEdge=rightAligned?event.getResolution().getScaledWidth()-posX:-1;
        int line=0;
        for(String label:rows) {
            int y=top+line*lineHeight;
            int textWidth=mc.fontRenderer.getStringWidth(label);
            int textX=rightAligned?rightEdge-textWidth-4:posX+4;
            int left=textX-4,right=textX+textWidth+4;
            Gui.drawRect(left,y-2,right,y+mc.fontRenderer.FONT_HEIGHT+1,0xCC0A0A0A);
            if(rightAligned)Gui.drawRect(right-2,y-2,right,y+mc.fontRenderer.FONT_HEIGHT+1,0xFFFFD700);
            else Gui.drawRect(left,y-2,left+2,y+mc.fontRenderer.FONT_HEIGHT+1,0xFFFFD700);
            mc.fontRenderer.drawStringWithShadow(label,textX,y,0xFFFFD700);
            line++;
        }
    }
}

