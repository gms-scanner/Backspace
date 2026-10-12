package dev.goldforge.gui;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Immediate-mode Backspace mark avoids resource-pack and texture-loader dependencies. */
public final class BrandMark {
    public static int hudX=8,hudY=8;
    public static boolean hudRight,hudBottom;
    private BrandMark() {}

    public static void draw(int x,int y,int size) {
        draw(x,y,size,1.0F);
    }

    /** Draw with caller-controlled alpha for low-contrast logo marks behind GUI copy. */
    public static void draw(int x,int y,int size,float alpha) {
        float left=x,top=y,s=size;
        float a=Math.max(0.0F,Math.min(1.0F,alpha));
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        GlStateManager.disableTexture2D();GlStateManager.disableCull();
        GlStateManager.color(1.0F,0.843F,0.0F,a);
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(left,top+s*.5F);
        GL11.glVertex2f(left+s*.31F,top+s*.14F);
        GL11.glVertex2f(left+s*.88F,top+s*.14F);
        GL11.glVertex2f(left+s*.98F,top+s*.24F);
        GL11.glVertex2f(left+s*.98F,top+s*.76F);
        GL11.glVertex2f(left+s*.88F,top+s*.86F);
        GL11.glVertex2f(left+s*.31F,top+s*.86F);
        GL11.glEnd();
        GlStateManager.color(0.039F,0.039F,0.039F,a);
        drawBar(left+s*.62F,top+s*.5F,-s*.12F,-s*.12F,s*.06F);
        drawBar(left+s*.62F,top+s*.5F,s*.12F,-s*.12F,s*.06F);
        GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();
        GlStateManager.color(1.0F,1.0F,1.0F,1.0F);GlStateManager.popMatrix();
    }

    private static void drawBar(float cx,float cy,float dx,float dy,float halfWidth) {
        float length=(float)Math.sqrt(dx*dx+dy*dy), px=-dy/length*halfWidth, py=dx/length*halfWidth;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(cx-dx+px,cy-dy+py);GL11.glVertex2f(cx+dx+px,cy+dy+py);
        GL11.glVertex2f(cx+dx-px,cy+dy-py);GL11.glVertex2f(cx-dx-px,cy-dy-py);
        GL11.glEnd();
    }
}
