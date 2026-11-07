package dev.goldforge.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import java.awt.Color;

/** Floating palette owned by the ClickGUI; no screen switch or parent redraw. */
final class ThemeWindow {
    UiFont font;
    boolean open;
    private boolean minimized;
    int x=16,y=60;
    static final int W=156,H=174;
    private int drag=-1,dx,dy;
    private float hue=.135F,saturation=.77F,brightness=.96F;
    private static final int[] PRESETS={0xF4CE38,0xE35D65,0x6DE0BB,0x75BBF7};
    void draw(int width,int height) {
        if(!open)return;
        x=Math.max(2,Math.min(x,width-W-2));y=Math.max(62,Math.min(y,height-H-24));
        WindowChrome.frame(x,y,W,minimized?17:H,0xF015171B,accent());
        WindowChrome.header(x,y,W,17,accent());
        text("THEME",x+6,y+4,0xFF141414);text("x",x+W-12,y+4,0xFF141414);
        text(minimized?"+":"-",x+W-27,y+4,0xFF141414);
        if(minimized)return;
        for(int a=0;a<128;a+=2)for(int b=0;b<72;b+=2)
            Gui.drawRect(x+8+a,y+24+b,x+10+a,y+26+b,0xFF000000|Color.HSBtoRGB(hue,a/126F,1-b/70F));
        for(int b=0;b<72;b++)Gui.drawRect(x+140,y+24+b,x+148,y+25+b,Color.HSBtoRGB(b/71F,1,1));
        int px=x+8+Math.round(saturation*126),py=y+24+Math.round((1-brightness)*70);
        Gui.drawRect(px-2,py-1,px+3,py+2,0xFFFFFFFF);
        text(String.format("#%06X",accent()&0xFFFFFF),x+8,y+101,0xFFE8E8E8);
        for(int i=0;i<4;i++)Gui.drawRect(x+8+i*35,y+116,x+37+i*35,y+132,0xFF000000|PRESETS[i]);
        text("Transparency "+GuiPreferences.pixelTransparency+"%",x+8,y+139,0xFFE8E8E8);
        Gui.drawRect(x+8,y+154,x+148,y+162,0xFF36383D);
        Gui.drawRect(x+8,y+154,x+8+GuiPreferences.pixelTransparency*140/100,y+162,accent());
    }
    private void text(String s,int x,int y,int c){if(font!=null)font.draw(s,x,y-1,c);else Minecraft.getMinecraft().fontRenderer.drawString(s,x,y,c);}
    int accent(){return GuiPreferences.customAccent>=0?0xFF000000|GuiPreferences.customAccent:0xFF000000|PRESETS[GuiPreferences.theme];}
    boolean click(int mx,int my,int button) {
        if(!open||mx<x||mx>=x+W||my<y||my>=y+(minimized?17:H))return false;
        if(button!=0)return true;
        if(my<y+17){if(mx>x+W-20)open=false;else if(mx>x+W-36)minimized=!minimized;else{drag=0;dx=mx-x;dy=my-y;}return true;}
        if(my>=y+24&&my<y+96){drag=mx>=x+138?2:1;move(mx,my);}
        else if(my>=y+116&&my<y+132&&mx>=x+8&&mx<x+148){int i=Math.min(3,(mx-x-8)/35);GuiPreferences.theme=i;GuiPreferences.customAccent=-1;float[] h=Color.RGBtoHSB((PRESETS[i]>>16)&255,(PRESETS[i]>>8)&255,PRESETS[i]&255,null);hue=h[0];saturation=h[1];brightness=h[2];}
        else if(my>=y+151&&my<y+166){drag=3;move(mx,my);}
        return true;
    }
    void move(int mx,int my){
        if(drag==0){x=mx-dx;y=my-dy;return;}
        if(drag==1){saturation=clamp((mx-x-8)/126F);brightness=1-clamp((my-y-24)/70F);}
        if(drag==2)hue=clamp((my-y-24)/71F);
        if(drag==1||drag==2)GuiPreferences.customAccent=Color.HSBtoRGB(hue,saturation,brightness)&0xFFFFFF;
        if(drag==3)GuiPreferences.pixelTransparency=Math.round(clamp((mx-x-8)/140F)*100);
    }
    boolean dragging(){return drag>=0;}
    void release(){drag=-1;}
    private static float clamp(float f){return Math.max(0,Math.min(1,f));}
}
