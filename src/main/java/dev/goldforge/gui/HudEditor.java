package dev.goldforge.gui;

import dev.goldforge.GoldForge;
import dev.goldforge.module.Module;
import dev.goldforge.module.impl.HudArrayList;
import dev.goldforge.module.impl.HudInfo;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Drag, corner-snap, and preview editor for the ArrayList and client brand HUD. */
public final class HudEditor extends GuiScreen {
    private static final int SNAP_DISTANCE=24, EDGE_GAP=8;
    private final GuiScreen parent;
    private HudArrayList hud;
    private HudInfo info;
    private int dragging;
    private int dragX,dragY;

    public HudEditor(GuiScreen parent) { this.parent=parent; }

    @Override public void initGui() {
        Module module=GoldForge.MODULES.get("ArrayList HUD");
        hud=module instanceof HudArrayList?(HudArrayList)module:null;
        Module infoModule=GoldForge.MODULES.get("Info HUD");
        info=infoModule instanceof HudInfo?(HudInfo)infoModule:null;
    }

    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks) {
        drawRect(0,0,width,height,0xB20A0A0A);
        drawCenteredString(fontRenderer,"BACKSPACE HUD EDITOR",width/2,18,0xFFFFD700);
        drawCenteredString(fontRenderer,"Drag either preview. Elements snap to an 8 px corner inset.",width/2,34,0xFFCCCCCC);
        if(hud!=null)drawArrayList();
        if(info!=null)drawInfo();
        drawBrand();
        button(width/2-138,height-51,132,20,"ARRAYLIST  "+(hud!=null&&hud.enabled?"ON":"OFF"),hud!=null&&hud.enabled);
        button(width/2+6,height-51,132,20,"INFO HUD  "+(info!=null&&info.enabled?"ON":"OFF"),info!=null&&info.enabled);
        drawCenteredString(fontRenderer,"ESC TO RETURN",width/2,height-18,0xFF777777);
    }

    private List<String> rows(){List<String> rows=hud.labels();if(rows.isEmpty())rows=previewRows();return rows;}
    private int arrayWidth(List<String> rows){int w=0;for(String row:rows)w=Math.max(w,fontRenderer.getStringWidth(row));return w+8;}
    private int arrayHeight(List<String> rows){return rows.size()*(fontRenderer.FONT_HEIGHT+3);}

    private void drawArrayList(){
        List<String> rows=rows();int maxWidth=arrayWidth(rows),lineHeight=fontRenderer.FONT_HEIGHT+3;
        int left=hud.rightAligned?width-hud.posX-maxWidth:hud.posX;
        int top=hud.bottomAnchored?height-hud.posY-arrayHeight(rows):hud.posY;
        left=clamp(left,4,Math.max(4,width-maxWidth-4));top=clamp(top,4,Math.max(4,height-arrayHeight(rows)-4));
        for(int i=0;i<rows.size();i++){
            String label=rows.get(i);int labelWidth=fontRenderer.getStringWidth(label);
            int textX=hud.rightAligned?left+maxWidth-4-labelWidth:left+4;
            int rowY=top+i*lineHeight,rowLeft=textX-4,rowRight=textX+labelWidth+4;
            Gui.drawRect(rowLeft,rowY-2,rowRight,rowY+fontRenderer.FONT_HEIGHT+1,0xE60A0A0A);
            if(hud.rightAligned)Gui.drawRect(rowRight-2,rowY-2,rowRight,rowY+fontRenderer.FONT_HEIGHT+1,0xFFFFD700);
            else Gui.drawRect(rowLeft,rowY-2,rowLeft+2,rowY+fontRenderer.FONT_HEIGHT+1,0xFFFFD700);
            fontRenderer.drawStringWithShadow(label,textX,rowY,0xFFFFD700);
        }
        hud.posX=hud.rightAligned?width-left-maxWidth:left;
        hud.posY=hud.bottomAnchored?height-top-arrayHeight(rows):top;
    }

    private int brandWidth(){return 18+6+fontRenderer.getStringWidth(GoldForge.NAME)+6+fontRenderer.getStringWidth(GoldForge.VERSION);}
    private void drawBrand(){
        int groupWidth=brandWidth();
        int x=BrandMark.hudRight?width-BrandMark.hudX-groupWidth:BrandMark.hudX;
        int y=BrandMark.hudBottom?height-BrandMark.hudY-18:BrandMark.hudY;
        x=clamp(x,4,Math.max(4,width-groupWidth-4));y=clamp(y,4,Math.max(4,height-22));
        BrandMark.draw(x,y,18);
        int textX=x+24;
        fontRenderer.drawStringWithShadow(GoldForge.NAME,textX,y+5,0xFFFFD700);
        textX+=fontRenderer.getStringWidth(GoldForge.NAME)+6;
        fontRenderer.drawString(GoldForge.VERSION,textX,y+6,0xFFF0F0F0);
        BrandMark.hudX=BrandMark.hudRight?width-x-groupWidth:x;
        BrandMark.hudY=BrandMark.hudBottom?height-y-18:y;
    }

    private void drawInfo(){
        String[] lines=info.editorPreviewLines();int max=0;for(String line:lines)max=Math.max(max,fontRenderer.getStringWidth(line));
        int left=clamp(info.x,4,Math.max(4,width-max-12)),top=clamp(info.y,4,Math.max(4,height-lines.length*(fontRenderer.FONT_HEIGHT+4)-65));
        int row=top;for(String line:lines){int textWidth=fontRenderer.getStringWidth(line);Gui.drawRect(left-4,row-2,left+textWidth+5,row+fontRenderer.FONT_HEIGHT+2,0xB80A0A0A);Gui.drawRect(left-4,row-2,left-2,row+fontRenderer.FONT_HEIGHT+2,0xFFFFD700);fontRenderer.drawStringWithShadow(line,left,row,0xFFF4F4F4);row+=fontRenderer.FONT_HEIGHT+4;}
        info.x=left;info.y=top;
    }

    private void button(int x,int y,int w,int h,String label,boolean active){Gui.drawRect(x,y,x+w,y+h,active?0xFFFFD700:0xFF252525);drawCenteredString(fontRenderer,label,x+w/2,y+6,active?0xFF0A0A0A:0xFFBBBBBB);}

    private void snapArray(int left,int top){
        List<String> rows=rows();int w=arrayWidth(rows),h=arrayHeight(rows);
        left=clamp(left,4,Math.max(4,width-w-4));top=clamp(top,4,Math.max(4,height-h-4));
        int rightDistance=width-(left+w);
        if(left<=SNAP_DISTANCE||rightDistance<=SNAP_DISTANCE){
            hud.rightAligned=rightDistance<left;
            if(Math.min(left,rightDistance)<=SNAP_DISTANCE)hud.posX=EDGE_GAP;
            else hud.posX=hud.rightAligned?rightDistance:left;
        }else{hud.rightAligned=rightDistance<left;hud.posX=hud.rightAligned?rightDistance:left;}
        int bottomDistance=height-(top+h);
        if(top<=SNAP_DISTANCE||bottomDistance<=SNAP_DISTANCE){
            hud.bottomAnchored=bottomDistance<top;
            if(Math.min(top,bottomDistance)<=SNAP_DISTANCE)hud.posY=EDGE_GAP;
            else hud.posY=hud.bottomAnchored?bottomDistance:top;
        }else{hud.bottomAnchored=bottomDistance<top;hud.posY=hud.bottomAnchored?bottomDistance:top;}
    }

    private void snapBrand(int x,int y){
        int w=brandWidth(),h=18;
        x=clamp(x,4,Math.max(4,width-w-4));y=clamp(y,4,Math.max(4,height-h-4));
        int rightDistance=width-(x+w);
        BrandMark.hudRight=rightDistance<x;
        BrandMark.hudX=Math.min(x,rightDistance)<=SNAP_DISTANCE?EDGE_GAP:(BrandMark.hudRight?rightDistance:x);
        int bottomDistance=height-(y+h);
        BrandMark.hudBottom=bottomDistance<y;
        BrandMark.hudY=Math.min(y,bottomDistance)<=SNAP_DISTANCE?EDGE_GAP:(BrandMark.hudBottom?bottomDistance:y);
    }

    private List<String> previewRows(){
        List<String> rows=new ArrayList<String>();rows.add("CrystalAura [NCP]");rows.add("TreeBurner");rows.add("AutoBanner");
        Collections.sort(rows,new Comparator<String>(){@Override public int compare(String a,String b){int n=Integer.compare(b.replace(" [NCP]","").length(),a.replace(" [NCP]","").length());return n!=0?n:a.compareToIgnoreCase(b);}});
        return rows;
    }

    private int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}

    @Override protected void mouseClicked(int mouseX,int mouseY,int mouseButton) throws IOException {
        if(hud==null)return;
        if(mouseY>=height-51&&mouseY<=height-31&&mouseX>=width/2-138&&mouseX<=width/2-6){if(hud!=null)hud.toggle();dev.goldforge.ConfigManager.save();return;}
        if(mouseY>=height-51&&mouseY<=height-31&&mouseX>=width/2+6&&mouseX<=width/2+138){if(info!=null)info.toggle();dev.goldforge.ConfigManager.save();return;}
        List<String> rows=rows();int listW=arrayWidth(rows),listH=arrayHeight(rows);
        int listLeft=hud.rightAligned?width-hud.posX-listW:hud.posX;
        int listTop=hud.bottomAnchored?height-hud.posY-listH:hud.posY;
        if(mouseX>=listLeft-4&&mouseX<=listLeft+listW&&mouseY>=listTop-3&&mouseY<=listTop+listH){dragging=1;dragX=mouseX-listLeft;dragY=mouseY-listTop;return;}
        if(info!=null){String[] lines=info.editorPreviewLines();int max=0;for(String line:lines)max=Math.max(max,fontRenderer.getStringWidth(line));int infoH=lines.length*(fontRenderer.FONT_HEIGHT+4);int infoLeft=info.x,infoTop=info.y;if(mouseX>=infoLeft-4&&mouseX<=infoLeft+max+6&&mouseY>=infoTop-3&&mouseY<=infoTop+infoH){dragging=3;dragX=mouseX-infoLeft;dragY=mouseY-infoTop;return;}}
        int brandW=brandWidth(),brandLeft=BrandMark.hudRight?width-BrandMark.hudX-brandW:BrandMark.hudX;
        int brandTop=BrandMark.hudBottom?height-BrandMark.hudY-18:BrandMark.hudY;
        if(mouseX>=brandLeft&&mouseX<=brandLeft+brandW&&mouseY>=brandTop&&mouseY<=brandTop+18){dragging=2;dragX=mouseX-brandLeft;dragY=mouseY-brandTop;return;}
        super.mouseClicked(mouseX,mouseY,mouseButton);
    }

    @Override protected void mouseClickMove(int mouseX,int mouseY,int clickedMouseButton,long timeSinceLastClick) {
        if(dragging==1&&hud!=null)snapArray(mouseX-dragX,mouseY-dragY);
        else if(dragging==2)snapBrand(mouseX-dragX,mouseY-dragY);
        else if(dragging==3&&info!=null){info.x=clamp(mouseX-dragX,4,Math.max(4,width-24));info.y=clamp(mouseY-dragY,4,Math.max(4,height-100));}
        super.mouseClickMove(mouseX,mouseY,clickedMouseButton,timeSinceLastClick);
    }
    @Override protected void mouseReleased(int mouseX,int mouseY,int state){if(dragging!=0)dev.goldforge.ConfigManager.save();dragging=0;super.mouseReleased(mouseX,mouseY,state);}
    @Override protected void keyTyped(char typedChar,int keyCode) throws IOException {if(keyCode==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}super.keyTyped(typedChar,keyCode);}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void drawDefaultBackground(){}
}
