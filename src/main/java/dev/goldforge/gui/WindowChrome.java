package dev.goldforge.gui;
import net.minecraft.client.gui.Gui;
/** Pixel window frame shared by category and utility windows. */
final class WindowChrome {
    static int shade(int c,float factor){return (c&0xFF000000)|((int)(((c>>16)&255)*factor)<<16)|((int)(((c>>8)&255)*factor)<<8)|(int)((c&255)*factor);}
    static void frame(int x,int y,int w,int h,int fill,int accent){
        if(h<=0)return;
        Gui.drawRect(x+w,y+3,x+w+3,y+h+3,0x35000000);
        Gui.drawRect(x+3,y+h,x+w,y+h+3,0x35000000);
        Gui.drawRect(x,y,x+w,y+h,shade(accent,.45F));
        if(w>2&&h>2)Gui.drawRect(x+1,y+1,x+w-1,y+h-1,fill);
        Gui.drawRect(x,y,x+w,y+1,accent);
        Gui.drawRect(x,y,x+1,y+h,shade(accent,.7F));
        Gui.drawRect(x+2,y+h-1,x+w-2,y+h,shade(accent,.7F));
    }
    static void header(int x,int y,int w,int h,int accent){
        for(int row=0;row<h;row++)Gui.drawRect(x,y+row,x+w,y+row+1,shade(accent,1F-.26F*row/Math.max(1,h-1)));
        Gui.drawRect(x+1,y+1,x+w-1,y+2,0x65FFFFFF);
        Gui.drawRect(x,y+h-1,x+w,y+h,shade(accent,.4F));
    }
}
