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

/** Compact, pixel edged category windows inspired by Minecraft's classic GUI scale. */
public final class PixelClickGui extends GuiScreen {
    private static final int YELLOW=0xFFF4CE38, BLACK=0xE8141515, EDGE=0xFF544916;
    private static final int W=136, GAP=7, ROW=17, TOP=30, HEADER=19, FOOT=27;
    private final GuiScreen parent;
    private final EnumMap<Category,Integer> scroll=new EnumMap<Category,Integer>(Category.class);
    private int page,settingsScroll;
    private Module selected;
    private boolean binding;
    public PixelClickGui(){this(null);}
    public PixelClickGui(GuiScreen parent){this.parent=parent;}

    private int visibleColumns(){return Math.max(1,(width-16+GAP)/(W+GAP));}
    private int visibleRows(){return Math.max(1,(height-TOP-HEADER-FOOT-10)/ROW);}
    private int maxPage(){return Math.max(0,Category.values().length-visibleColumns());}
    private int left(int column){return 8+column*(W+GAP);}
    private void box(int x,int y,int w,int h,int fill){Gui.drawRect(x,y,x+w,y+h,EDGE);Gui.drawRect(x+1,y+1,x+w-1,y+h-1,fill);}
    private void text(String s,int x,int y,int color){fontRenderer.drawStringWithShadow(s,x,y,color);}
    private String fit(String s,int max){return fontRenderer.trimStringToWidth(s,max);}

    @Override public void drawScreen(int mx,int my,float partial){
        Gui.drawRect(0,0,width,height,0x63000000);
        text("BACKSPACE",8,8,YELLOW);
        text("<  CATEGORIES  >",Math.max(8,width-100),8,0xFFE0E0D0);
        Category[] categories=Category.values();page=Math.min(page,maxPage());
        for(int col=0;col<visibleColumns()&&page+col<categories.length;col++){
            Category category=categories[page+col];int x=left(col);List<Module> modules=GoldForge.MODULES.in(category);
            int shown=Math.min(modules.size(),visibleRows());int y=TOP;
            box(x,y,W,HEADER,YELLOW);
            text(category.name(),x+5,y+5,0xFF101010);
            text(Integer.toString(modules.size()),x+W-18,y+5,0xFF101010);
            int offset=Math.max(0,Math.min(scroll.containsKey(category)?scroll.get(category):0,Math.max(0,modules.size()-visibleRows())));
            scroll.put(category,offset);
            box(x,y+HEADER,W,shown*ROW+2,BLACK);
            for(int i=0;i<shown;i++){
                Module m=modules.get(offset+i);int rowY=y+HEADER+1+i*ROW;
                boolean hover=mx>=x&&mx<x+W&&my>=rowY&&my<rowY+ROW;
                if(hover)Gui.drawRect(x+1,rowY,x+W-1,rowY+ROW,0xAA6B591C);
                if(m.enabled)Gui.drawRect(x+1,rowY,x+3,rowY+ROW,YELLOW);
                text(fit(m.name,W-27),x+6,rowY+4,m.enabled?YELLOW:0xFFE5E5D6);
                circle(x+W-12,rowY+8,m.enabled);
            }
            if(modules.size()>shown)text((offset+1)+"-"+(offset+shown)+" / "+modules.size(),x+4,y+HEADER+shown*ROW+5,0xFFE9CF76);
        }
        text("LMB TOGGLE  |  RMB SETTINGS  |  WHEEL SCROLL  |  HUD EDITOR",8,height-13,0xFFEAD993);
        box(width-91,height-19,84,16,YELLOW);text("HUD EDITOR",width-85,height-15,0xFF101010);
        if(selected!=null)drawSettings(mx,my);
    }

    private void circle(int cx,int cy,boolean enabled){
        // Eight by eight stepped ring reads as a pixel circle at the default GUI scale.
        int[][] pixels={{-2,-4},{-1,-4},{0,-4},{1,-4},{-3,-3},{2,-3},{-4,-2},{3,-2},{-4,-1},{3,-1},{-4,0},{3,0},{-3,1},{2,1},{-2,2},{-1,2},{0,2},{1,2}};
        for(int[] p:pixels)Gui.drawRect(cx+p[0],cy+p[1],cx+p[0]+1,cy+p[1]+1,YELLOW);
        if(enabled)Gui.drawRect(cx-2,cy-2,cx+2,cy+1,YELLOW);
    }

    private int panelX(){return Math.max(8,Math.min(width-171,width-177));}
    private int settingsRows(){return Math.max(1,Math.min(9,(height-101)/20));}
    private void drawSettings(int mx,int my){
        int x=panelX(),y=TOP,w=166;List<ModuleSetting> options=selected.getSettings();
        int rows=Math.min(settingsRows(),options.size());
        box(x,y,w,HEADER+32+rows*20+29,BLACK);box(x,y,w,HEADER,YELLOW);
        text(fit(selected.name,w-35),x+6,y+5,0xFF101010);text("X",x+w-12,y+5,0xFF101010);
        text(fit(selected.description,w-11),x+5,y+24,0xFFC4C4B6);
        box(x+5,y+37,w-10,16,selected.enabled?YELLOW:0xFF414039);
        text(selected.enabled?"ENABLED":"DISABLED",x+11,y+41,selected.enabled?0xFF111111:0xFFF1F1E4);
        settingsScroll=Math.max(0,Math.min(settingsScroll,Math.max(0,options.size()-settingsRows())));
        for(int i=0;i<rows;i++){
            ModuleSetting option=options.get(settingsScroll+i);int ry=y+57+i*20;
            Gui.drawRect(x+5,ry,x+w-5,ry+18,0xFF272723);
            text(fit(option.name,92),x+9,ry+5,0xFFE8E7D9);
            text("-",x+w-49,ry+5,YELLOW);text(Integer.toString(option.get()),x+w-37,ry+5,YELLOW);text("+",x+w-13,ry+5,YELLOW);
        }
        int bottom=y+57+rows*20;
        box(x+5,bottom,w-10,17,0xFF423C23);
        text(binding?"PRESS A KEY...":"KEY: "+(selected.key==0?"NONE":Keyboard.getKeyName(selected.key)),x+9,bottom+4,YELLOW);
        if(special())text("RIGHT CLICK: OPEN EDITOR",x+6,bottom+21,YELLOW);
    }
    private boolean special(){return selected instanceof BlockESP||selected instanceof DIESP||selected instanceof AutoSign||selected instanceof Hands;}
    private void openEditor(){
        if(selected instanceof BlockESP)mc.displayGuiScreen(new BlockSelectGui(this,(BlockESP)selected));
        else if(selected instanceof DIESP)mc.displayGuiScreen(new ItemSelectGui(this,(DIESP)selected));
        else if(selected instanceof AutoSign)mc.displayGuiScreen(new SignTextGui(this,(AutoSign)selected));
        else if(selected instanceof Hands)((Hands)selected).openEditor();
    }
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(mx>=width-91&&mx<width-7&&my>=height-19&&my<height-3){mc.displayGuiScreen(new HudEditor(this));return;}
        if(selected!=null){
            int x=panelX(),y=TOP,w=166;
            if(mx>=x&&mx<x+w&&my>=y&&my<y+HEADER&&mx>x+w-25){selected=null;binding=false;return;}
            if(mx>=x+5&&mx<x+w-5&&my>=y+37&&my<y+53){selected.toggle();return;}
            List<ModuleSetting> options=selected.getSettings();int rows=Math.min(settingsRows(),options.size());
            for(int i=0;i<rows;i++){int ry=y+57+i*20;if(mx<x+5||mx>=x+w-5||my<ry||my>=ry+18)continue;
                if(mx>=x+w-55){options.get(settingsScroll+i).adjust(mx>=x+w-23?1:-1);return;}
            }
            int bottom=y+57+rows*20;
            if(mx>=x+5&&mx<x+w-5&&my>=bottom&&my<bottom+17){binding=true;return;}
            if(special()&&mx>=x&&mx<x+w&&my>=bottom+19&&my<bottom+38){openEditor();return;}
            if(mx>=x&&mx<x+w&&my>=y){return;}
        }
        Category[] categories=Category.values();
        for(int col=0;col<visibleColumns()&&page+col<categories.length;col++){
            int x=left(col);Category category=categories[page+col];List<Module> modules=GoldForge.MODULES.in(category);
            int index=(scroll.containsKey(category)?scroll.get(category):0)+(my-TOP-HEADER-1)/ROW;
            if(mx<x||mx>=x+W||my<TOP+HEADER+1||my>=TOP+HEADER+1+Math.min(visibleRows(),modules.size())*ROW||index<0||index>=modules.size())continue;
            Module module=modules.get(index);
            if(button==0)module.toggle();else if(button==1){selected=module;settingsScroll=0;binding=false;}
            return;
        }
        if(my<23){if(mx<105)page=Math.max(0,page-1);else if(mx>width-110)page=Math.min(maxPage(),page+1);}
    }
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel==0)return;
        int mx=Mouse.getEventX()*width/mc.displayWidth,my=height-Mouse.getEventY()*height/mc.displayHeight-1;
        if(selected!=null&&mx>=panelX()&&mx<panelX()+166){settingsScroll=Math.max(0,Math.min(Math.max(0,selected.getSettings().size()-settingsRows()),settingsScroll+(wheel<0?1:-1)));return;}
        for(int col=0;col<visibleColumns()&&page+col<Category.values().length;col++)if(mx>=left(col)&&mx<left(col)+W){
            Category cat=Category.values()[page+col];int max=Math.max(0,GoldForge.MODULES.in(cat).size()-visibleRows());
            scroll.put(cat,Math.max(0,Math.min(max,(scroll.containsKey(cat)?scroll.get(cat):0)+(wheel<0?2:-2))));return;
        }
        page=Math.max(0,Math.min(maxPage(),page+(wheel<0?1:-1)));
    }
    @Override protected void keyTyped(char ch,int key)throws IOException{
        if(binding&&selected!=null){selected.setKey(key==Keyboard.KEY_ESCAPE?0:key);binding=false;return;}
        if(key==Keyboard.KEY_ESCAPE){if(selected!=null){selected=null;return;}mc.displayGuiScreen(parent);return;}
        if(key==Keyboard.KEY_LEFT)page=Math.max(0,page-1);
        else if(key==Keyboard.KEY_RIGHT)page=Math.min(maxPage(),page+1);
        super.keyTyped(ch,key);
    }
    @Override public void onGuiClosed(){ConfigManager.save();}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void drawDefaultBackground(){}
}
