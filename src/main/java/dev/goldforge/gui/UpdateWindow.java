package dev.goldforge.gui;

import dev.goldforge.BackspaceUpdater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/** Compact updater panel owned by the ClickGUI. */
final class UpdateWindow {
    static final int W=222,H=104;
    boolean open;
    int x=16,y=62;
    UiFont font;
    private boolean dragging;
    private int dx,dy;
    void draw(int screenW,int screenH,int accent) {
        if(!open)return;
        x=Math.max(2,Math.min(x,Math.max(2,screenW-W-2)));
        y=Math.max(26,Math.min(y,Math.max(26,screenH-H-25)));
        WindowChrome.frame(x,y,W,H,0xF015171B,accent);
        WindowChrome.header(x,y,W,17,accent);
        text("BACKSPACE UPDATE",x+7,y+4,0xFF171717);
        text("x",x+W-13,y+4,0xFF171717);
        BackspaceUpdater.Status status=BackspaceUpdater.status();
        String title=status.phase==BackspaceUpdater.Phase.CHECKING?"Backspace is checking your version...":
                status.phase==BackspaceUpdater.Phase.DOWNLOADING?"Updating now":status.detail;
        text(fit(title,W-16),x+8,y+24,0xFFEAE6D4);
        if(status.phase==BackspaceUpdater.Phase.DOWNLOADING)
            text(fit(status.version,W-16),x+8,y+38,accent);
        else if(status.phase==BackspaceUpdater.Phase.READY)
            text("Replacement installs after exit",x+8,y+38,0xFFC5C1AE);
        else if(status.phase==BackspaceUpdater.Phase.FAILED)
            text("Check connection and retry",x+8,y+38,0xFFC5C1AE);
        else text("Current: "+dev.goldforge.GoldForge.VERSION,x+8,y+38,0xFFC5C1AE);
        Gui.drawRect(x+8,y+56,x+W-8,y+64,0xFF36352E);
        float progress=status.phase==BackspaceUpdater.Phase.CHECKING
                ? (System.currentTimeMillis()%1400L)/1400F : status.progress;
        int track=W-16;
        if(status.phase==BackspaceUpdater.Phase.CHECKING){
            int start=x+8+Math.round((track-38)*progress);
            Gui.drawRect(start,y+56,start+38,y+64,accent);
        }else Gui.drawRect(x+8,y+56,x+8+Math.round(track*progress),y+64,accent);
        Gui.drawRect(x+8,y+75,x+W-8,y+96,accent);
        String button=status.phase==BackspaceUpdater.Phase.READY?"CLOSE GAME TO INSTALL":
                status.phase==BackspaceUpdater.Phase.CHECKING||status.phase==BackspaceUpdater.Phase.DOWNLOADING
                ?"WORKING...":"CHECK FOR UPDATES";
        text(button,x+15,y+81,0xFF171717);
    }
    private String fit(String value,int width){
        if(font==null)return value.length()>37?value.substring(0,34)+"...":value;
        return font.trimToWidth(value,width);
    }
    private void text(String value,int tx,int ty,int color){
        if(font!=null)font.draw(value,tx,ty-1,color);
        else Minecraft.getMinecraft().fontRenderer.drawString(value,tx,ty,color);
    }
    boolean click(int mx,int my,int button){
        if(!open||mx<x||mx>=x+W||my<y||my>=y+H)return false;
        if(button!=0)return true;
        if(my<y+17){
            if(mx>=x+W-20)open=false;
            else{dragging=true;dx=mx-x;dy=my-y;}
        }else if(my>=y+75){
            BackspaceUpdater.Phase phase=BackspaceUpdater.status().phase;
            if(phase==BackspaceUpdater.Phase.READY)Minecraft.getMinecraft().shutdown();
            else if(phase!=BackspaceUpdater.Phase.CHECKING && phase!=BackspaceUpdater.Phase.DOWNLOADING)
                BackspaceUpdater.check(true);
        }
        return true;
    }
    void move(int mx,int my){if(dragging){x=mx-dx;y=my-dy;}}
    boolean dragging(){return dragging;}
    void release(){dragging=false;}
}
