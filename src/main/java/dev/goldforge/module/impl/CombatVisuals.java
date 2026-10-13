package dev.goldforge.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/** Scoped, camera-relative world primitives shared by the 3.5 visual modules. */
final class CombatVisuals {
    private CombatVisuals() {}
    static void begin() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glLineWidth(2F);
    }
    static void end() {
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GL11.glPopAttrib();
    }
    static void outline(AxisAlignedBB worldBox, float red, float green, float blue, float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        double x = mc.getRenderManager().viewerPosX, y = mc.getRenderManager().viewerPosY, z = mc.getRenderManager().viewerPosZ;
        RenderGlobal.drawSelectionBoundingBox(worldBox.offset(-x, -y, -z), red, green, blue, alpha);
    }
    static void fill(AxisAlignedBB worldBox, float red, float green, float blue, float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        double x = mc.getRenderManager().viewerPosX, y = mc.getRenderManager().viewerPosY, z = mc.getRenderManager().viewerPosZ;
        RenderGlobal.renderFilledBox(worldBox.offset(-x, -y, -z), red, green, blue, alpha);
    }
    static void line(double x1,double y1,double z1,double x2,double y2,double z2,float r,float g,float b,float a) {
        Minecraft mc=Minecraft.getMinecraft();
        double x=mc.getRenderManager().viewerPosX,y=mc.getRenderManager().viewerPosY,z=mc.getRenderManager().viewerPosZ;
        GL11.glColor4f(r,g,b,a);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x1-x,y1-y,z1-z);GL11.glVertex3d(x2-x,y2-y,z2-z);
        GL11.glEnd();
    }
    static void label(String text,double x,double y,double z,int color) {
        Minecraft mc=Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x-mc.getRenderManager().viewerPosX,y-mc.getRenderManager().viewerPosY,z-mc.getRenderManager().viewerPosZ);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY,0F,1F,0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX,1F,0F,0F);
            GlStateManager.scale(-.025F,-.025F,.025F);
            GlStateManager.enableTexture2D();
            mc.fontRenderer.drawStringWithShadow(text,-mc.fontRenderer.getStringWidth(text)/2F,0F,color);
            GlStateManager.disableTexture2D();
        } finally { GlStateManager.popMatrix(); }
    }
}
